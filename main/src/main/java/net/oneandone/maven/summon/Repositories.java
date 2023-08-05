package net.oneandone.maven.summon;

import org.apache.maven.classrealm.ClassRealmManager;
import org.apache.maven.project.DefaultProjectBuilder;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingHelper;
import org.apache.maven.repository.internal.ArtifactDescriptorUtils;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Profile;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.SettingsUtils;
import org.apache.maven.settings.building.DefaultSettingsBuilder;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryListener;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.internal.impl.DefaultRepositorySystem;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationSelector;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.ProxySelector;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.transfer.TransferListener;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.eclipse.aether.util.repository.DefaultMirrorSelector;
import org.eclipse.aether.util.repository.DefaultProxySelector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents configuration with settings and local configuration.
 */
public record Repositories(PlexusContainer container, Settings settings,
                           DefaultRepositorySystem repositorySystem, RepositorySystemSession repositorySession,
                           DefaultProjectBuilder projectBuilder,
                           List<RemoteRepository> repositories, List<RemoteRepository> pluginRepositories) {
    public static Repositories create(File localRepository, File globalSettings, File userSettings,
                               List<String> allowExtensions, List<String> allowPomRepositories, TransferListener transferListener, RepositoryListener repositoryListener)
            throws IOException {
        return create(localRepository, globalSettings, userSettings, createContainer(), allowExtensions, allowPomRepositories,
                transferListener, repositoryListener);
    }

    /**
     * @param allowExtensions null to allow all, empty array to forbid all
     */
    public static Repositories create(File localRepository, File globalSettings, File userSettings,
                               DefaultPlexusContainer container, List<String> allowExtensions, List<String> allowPomRepositories,
                               TransferListener transferListener, RepositoryListener repositoryListener) throws IOException {
        DefaultRepositorySystem system;
        DefaultRepositorySystemSession session;
        Settings settings;
        List<RemoteRepository> repositories;
        List<RemoteRepository> pluginRepositories;

        try {
            ExtensionBlocker rm = (ExtensionBlocker) container.lookup(ClassRealmManager.class);
            if (allowExtensions != null) {
                rm.getAllowArtifacts().addAll(allowExtensions);
            }
            settings = loadSettings(globalSettings, userSettings, container);
            system = (DefaultRepositorySystem) container.lookup(RepositorySystem.class);
            session = createSession(transferListener, repositoryListener, system,
                    createLocalRepository(localRepository, settings), settings);
            repositories = new ArrayList<>();
            pluginRepositories = new ArrayList<>();
            createRemoteRepositories(settings, repositories, pluginRepositories);
            PomRepositoryBlocker pm = (PomRepositoryBlocker) container.lookup(ProjectBuildingHelper.class);
            if (allowPomRepositories != null) {
                for (String url : allowPomRepositories) {
                    pm.allow(url);
                }
            }
            return new Repositories(container, settings, system, session,
                    (DefaultProjectBuilder) container.lookup(ProjectBuilder.class), repositories, pluginRepositories);
        } catch (ComponentLookupException e) {
            throw new IllegalStateException(e);
        }
    }

    private static LocalRepository createLocalRepository(File localRepository, Settings settings) {
        String localRepositoryStr;

        if (localRepository == null) {
            // TODO: who has precedence: repodir from settings or from MAVEN_OPTS
            localRepositoryStr = settings.getLocalRepository();
            if (localRepositoryStr == null) {
                localRepositoryStr = localRepositoryPathFromMavenOpts();
                if (localRepositoryStr == null) {
                    localRepositoryStr = defaultLocalRepositoryDir().getAbsolutePath();
                }
            }
        } else {
            localRepositoryStr = localRepository.getAbsolutePath();
        }
        return new LocalRepository(localRepositoryStr);
    }

    private static void createRemoteRepositories(Settings settings,
                                                 List<RemoteRepository> resultRepositories, List<RemoteRepository> resultPluginRepositories) {
        boolean central;
        boolean pluginCentral;
        List<String> actives;

        central = false;
        pluginCentral = false;
        actives = settings.getActiveProfiles();
        for (Profile profile : settings.getProfiles()) {
            if (actives.contains(profile.getId()) || (profile.getActivation() != null && profile.getActivation().isActiveByDefault())) {
                var p = SettingsUtils.convertFromSettingsProfile(profile);
                central = convert(p.getRepositories(), resultRepositories);
                pluginCentral = convert(p.getPluginRepositories(), resultPluginRepositories);
            }
        }
    /* Maven defines the default central repository in its master parent - and not in the default settings, which I'd prefer.
       As a consequent, central is not always defined when loading the settings.
       I first added central to repositories only, because legacy repositories are used to load poms which ultimatly load the
       master parent with it's repository definition. However, the parent might have to be loaded from central, so repositories
       also need a central definition. */
        if (!central) {
            resultRepositories.add(createCentral());
        }
        if (!pluginCentral) {
            resultPluginRepositories.add(createCentral());
        }
    }

    public static final String CENTRAL_URL = "https://repo.maven.apache.org/maven2";
    private static final org.apache.maven.model.Repository CENTRAL;

    static {
        org.apache.maven.model.RepositoryPolicy release = new org.apache.maven.model.RepositoryPolicy();
        org.apache.maven.model.RepositoryPolicy snapshot = new org.apache.maven.model.RepositoryPolicy();

        release.setEnabled("true");
        release.setUpdatePolicy("daily");
        release.setChecksumPolicy("warn");
        snapshot.setEnabled("false");
        CENTRAL = new org.apache.maven.model.Repository();
        CENTRAL.setId("central");
        CENTRAL.setUrl(CENTRAL_URL);
        CENTRAL.setReleases(release);
        CENTRAL.setSnapshots(snapshot);
    }

    private static RemoteRepository createCentral() {
        return ArtifactDescriptorUtils.toRemoteRepository(CENTRAL);
    }

    /**
     * @return true if central was included
     */
    private static boolean convert(List<org.apache.maven.model.Repository> src, List<RemoteRepository> dest) {
        boolean central;
        RemoteRepository converted;

        central = false;
        for (org.apache.maven.model.Repository repository : src) {
            converted = ArtifactDescriptorUtils.toRemoteRepository(repository);
            if ("central".equals(converted.getId())) {
                central = true;
            }
            dest.add(converted);
        }
        return central;
    }

    private static DefaultRepositorySystemSession createSession(TransferListener transferListener, RepositoryListener repositoryListener,
                                                                RepositorySystem system, LocalRepository localRepository, Settings settings) {
        DefaultRepositorySystemSession session;
        final List<Server> servers;

        servers = settings.getServers();
        session = MavenRepositorySystemUtils.newSession();

        // TODO need this?
        // session.setCache(new DefaultRepositoryCache());
        // session.setWorkspaceReader(MavenChainedWorkspaceReader.of(Collections.emptyList()));

        session.setAuthenticationSelector(new AuthenticationSelector() {
            @Override
            public Authentication getAuthentication(RemoteRepository repository) {
                Server server;

                server = lookup(repository.getId());
                if (server != null) {
                    if (server.getPassphrase() != null) {
                        throw new UnsupportedOperationException();
                    }
                    if (server.getPrivateKey() != null) {
                        throw new UnsupportedOperationException();
                    }
                    if (server.getUsername() != null) {
                        if (server.getPassword() == null) {
                            throw new IllegalStateException("missing password");
                        }
                        return new AuthenticationBuilder().addUsername(server.getUsername()).addPassword(server.getPassword()).build();
                    }
                    throw new UnsupportedOperationException();
                }
                return null;
            }

            private Server lookup(String id) {
                for (Server server : servers) {
                    if (id.equals(server.getId())) {
                        return server;
                    }
                }
                return null;
            }
        });
        if (repositoryListener != null) {
            session.setRepositoryListener(repositoryListener);
        }
        if (transferListener != null) {
            session.setTransferListener(transferListener);
        }
        session.setOffline(settings.isOffline());
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepository));
        DefaultMirrorSelector mirrorSelector = new DefaultMirrorSelector();
        for (Mirror mirror : settings.getMirrors()) {
            mirrorSelector.add(mirror.getId(), mirror.getUrl(), mirror.getLayout(), false, false, mirror.getMirrorOf(),
                    mirror.getMirrorOfLayouts());
        }
        session.setMirrorSelector(mirrorSelector);
        session.setProxySelector(getProxySelector(settings));
        return session;
    }

    private static ProxySelector getProxySelector(Settings settings) {
        DefaultProxySelector selector;
        AuthenticationBuilder builder;

        selector = new DefaultProxySelector();
        for (org.apache.maven.settings.Proxy proxy : settings.getProxies()) {
            builder = new AuthenticationBuilder();
            builder.addUsername(proxy.getUsername()).addPassword(proxy.getPassword());
            selector.add(new org.eclipse.aether.repository.Proxy(proxy.getProtocol(), proxy.getHost(),
                            proxy.getPort(), builder.build()),
                    proxy.getNonProxyHosts());
        }

        return selector;
    }

    //--

    public static File defaultLocalRepositoryDir() {
        return new File(IO.userHome(), ".m2/repository");
    }

    public static DefaultPlexusContainer createContainer() {
        return createContainer(null, null, Logger.LEVEL_DISABLED);
    }

    // mimics the respective MavenCli code
    public static DefaultPlexusContainer createContainer(ClassWorld classWorld, ClassRealm realm, int loglevel) {
        DefaultContainerConfiguration config;
        DefaultPlexusContainer container;

        config = new DefaultContainerConfiguration();
        if (classWorld != null) {
            config.setClassWorld(classWorld);
        }
        if (realm != null) {
            config.setRealm(realm);
        }
        config.setClassPathScanning(PlexusConstants.SCANNING_INDEX);
        config.setAutoWiring(true);
        config.setJSR250Lifecycle(true);
        try {
            container = new DefaultPlexusContainer(config);
        } catch (PlexusContainerException e) {
            throw new IllegalStateException(e);
        }
        container.setLookupRealm(null);
        container.getLoggerManager().setThreshold(loglevel);
        return container;
    }

    /**
     * Stripped down version of Maven SettingsXmlConfigurationProcessor. Unfortunately, there's no
     * easy way to reuse the code there, so I have to repeat much of the logic
     *
     * @param globalSettings null to use default
     * @param userSettings   null to use default
     */
    public static Settings loadSettings(File globalSettings, File userSettings, DefaultPlexusContainer container)
            throws IOException {
        DefaultSettingsBuilder builder;
        SettingsBuildingRequest request;

        try {
            builder = (DefaultSettingsBuilder) container.lookup(SettingsBuilder.class);
        } catch (ComponentLookupException e) {
            throw new IllegalStateException(e);
        }
        request = new DefaultSettingsBuildingRequest();
        if (globalSettings == null) {
            globalSettings = new File(locateMavenConf(), "settings.xml");
        }
        if (userSettings == null) {
            userSettings = new File(IO.userHome(), ".m2/settings.xml");
        }
        request.setGlobalSettingsFile(globalSettings);
        request.setUserSettingsFile(userSettings);
        try {
            return builder.build(request).getEffectiveSettings();
        } catch (SettingsBuildingException e) {
            throw new IOException("failed to load settings: " + e.getMessage(), e);
        }
    }

    private static String localRepositoryPathFromMavenOpts() {
        String value;

        value = System.getenv("MAVEN_OPTS");
        if (value != null) {
            for (String entry : value.split(" ")) {
                if (entry.startsWith("-Dmaven.repo.local=")) {
                    return entry.substring(entry.indexOf('=') + 1);
                }
            }
        }
        return null;
    }

    public static File locateMavenConf() throws IOException {
        String home;
        File mvn;
        File conf;

        home = System.getenv("MAVEN_HOME");
        if (home != null) {
            conf = new File(home, "conf");
            if (!conf.isDirectory()) {
                throw new IOException("MAVEN_HOME does not contain a conf directory: " + conf);
            }
            return conf;
        }
        mvn = IO.which("mvn");
        if (mvn != null) {
            mvn = IO.resolveSymbolicLinks(mvn);
            mvn = mvn.getParentFile().getParentFile();
            conf = new File(mvn, "conf");
            if (conf.isDirectory()) {
                return conf;
            }
            conf = new File(mvn, "libexec/conf");
            if (conf.isDirectory()) {
                return conf;
            }
        }

        throw new IOException("cannot locate maven's conf directory - consider settings MAVEN_HOME or adding mvn to your path");
    }
}
