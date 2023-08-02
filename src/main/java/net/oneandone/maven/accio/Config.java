/*
 * Copyright 1&1 Internet AG, https://github.com/1and1/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.oneandone.maven.accio;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.classrealm.ClassRealmManager;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.repository.legacy.LegacyRepositorySystem;
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
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryListener;
import org.eclipse.aether.RepositorySystem;
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

public record Config(PlexusContainer container,
                     RepositorySystem repositorySystem, DefaultRepositorySystemSession repositorySession, ProjectBuilder builder,
                     List<RemoteRepository> remote, ArtifactRepository localLegacy, List<ArtifactRepository> remoteLegacy, List<ArtifactRepository> pluginRemoteLegacy) {
    public static Config create() throws IOException {
        return create(null, null, null);
    }

    /**
     * @param localRepository null to use default
     * @param globalSettings null to use default
     * @param userSettings null to use default
     */
    public static Config create(File localRepository, File globalSettings, File userSettings, String... allowedExtensions)
            throws IOException {
        return create(localRepository, globalSettings, userSettings, createContainer(), allowedExtensions, null, null);
    }

    /**
     * @param allowedExtensions null to allow all, empty array to forbid all
     */
    public static Config create(File localRepository, File globalSettings, File userSettings,
                                DefaultPlexusContainer container, String[] allowedExtensions,
                                TransferListener transferListener, RepositoryListener repositoryListener) throws IOException {
        RepositorySystem system;
        DefaultRepositorySystemSession session;
        Settings settings;
        LegacyRepositorySystem legacySystem;
        List<ArtifactRepository> repositoriesLegacy;
        List<ArtifactRepository> pluginRepositoriesLegacy;
        LocalRepository lr;

        try {
            RestrictedClassRealmManager pm = (RestrictedClassRealmManager) container.lookup(ClassRealmManager.class);
            if (allowedExtensions != null) {
                pm.restrict(allowedExtensions);
            }
            try {
                settings = loadSettings(globalSettings, userSettings, container);
            } catch (SettingsBuildingException | XmlPullParserException e) {
                throw new IOException("cannot load settings: " + e.getMessage(), e);
            }
            system = container.lookup(RepositorySystem.class);
            lr = createLocalRepository(localRepository, settings);
            session = createSession(transferListener, repositoryListener, system, lr, settings);
            legacySystem = (LegacyRepositorySystem) container.lookup(org.apache.maven.repository.RepositorySystem.class, "default");
            repositoriesLegacy = new ArrayList<>();
            pluginRepositoriesLegacy = new ArrayList<>();
            repositoriesLegacy(legacySystem, settings, repositoriesLegacy, pluginRepositoriesLegacy);
            legacySystem.injectAuthentication(session, repositoriesLegacy);
            legacySystem.injectMirror(session, repositoriesLegacy);
            legacySystem.injectProxy(session, repositoriesLegacy);
            return new Config(container, system, session, container.lookup(ProjectBuilder.class),
                    RepositoryUtils.toRepos(repositoriesLegacy), legacySystem.createLocalRepository(lr.getBasedir()), repositoriesLegacy, pluginRepositoriesLegacy);
        } catch (InvalidRepositoryException | ComponentLookupException e) {
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
        container.getLoggerManager().setThreshold(loglevel);
        return container;
    }

    /**
     * @param globalSettings null to use default
     * @param userSettings null to use default
     */
    public static Settings loadSettings(File globalSettings, File userSettings, DefaultPlexusContainer container)
            throws IOException, XmlPullParserException, ComponentLookupException, SettingsBuildingException {
        DefaultSettingsBuilder builder;
        SettingsBuildingRequest request;

        builder = (DefaultSettingsBuilder) container.lookup(SettingsBuilder.class);
        request = new DefaultSettingsBuildingRequest();
        if (globalSettings == null) {
            globalSettings = new File(locateMavenConf(), "settings.xml");
        }
        if (userSettings == null) {
            userSettings = new File(IO.userHome(), ".m2/settings.xml");
        }
        request.setGlobalSettingsFile(globalSettings);
        request.setUserSettingsFile(userSettings);
        return builder.build(request).getEffectiveSettings();
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

    private static void repositoriesLegacy(LegacyRepositorySystem legacy, Settings settings,
                                           List<ArtifactRepository> resultRepositories, List<ArtifactRepository> resultPluginRepositories)
            throws InvalidRepositoryException {
        boolean central;
        List<String> actives;
        ArtifactRepository artifactRepository;

        central = false;
        actives = settings.getActiveProfiles();
        for (Profile profile : settings.getProfiles()) {
            if (actives.contains(profile.getId()) || (profile.getActivation() != null && profile.getActivation().isActiveByDefault())) {
                for (org.apache.maven.model.Repository repository : SettingsUtils.convertFromSettingsProfile(profile).getRepositories()) {
                    artifactRepository = legacy.buildArtifactRepository(repository);
                    if ("central".equals(artifactRepository.getId())) {
                        central = true;
                    }
                    resultRepositories.add(artifactRepository);
                }
                for (org.apache.maven.model.Repository repository : SettingsUtils.convertFromSettingsProfile(profile).getPluginRepositories()) {
                    artifactRepository = legacy.buildArtifactRepository(repository);
                    resultPluginRepositories.add(artifactRepository);
                }
            }
        }
        if (!central) {
            /* Maven defines the default central repository in its master parent - and not in the default settings, which I'd prefer.
               As a consequent, central is not always defined when loading the settings.
               I first added central to repositories only, because legacy repositories are used to load poms which ultimatly load the
               master parent with it's repository definition. However, the parent might have to be loaded from central, so repositories
               also need a central definition. */
            resultRepositories.add(legacy.createDefaultRemoteRepository());
        }
    }
}
