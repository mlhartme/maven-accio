package net.oneandone.maven.summon.api;

import org.apache.maven.repository.internal.ArtifactDescriptorUtils;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Profile;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.SettingsUtils;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
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

public record ModernRepositories(DefaultRepositorySystem repositorySystem, RepositorySystemSession repositorySession,
                                 List<RemoteRepository> repositories, List<RemoteRepository> pluginRepositories) {
    public static ModernRepositories create(PlexusContainer container, Settings settings, File localRepository,
                                            TransferListener transferListener, RepositoryListener repositoryListener) throws IOException {
        DefaultRepositorySystem system;
        DefaultRepositorySystemSession session;
        List<RemoteRepository> repositories;
        List<RemoteRepository> pluginRepositories;

        try {
            system = (DefaultRepositorySystem) container.lookup(RepositorySystem.class);
            session = createSession(transferListener, repositoryListener, system,
                    createLocalRepository(localRepository, settings), settings);
            repositories = new ArrayList<>();
            pluginRepositories = new ArrayList<>();
            createRemoteRepositories(settings, repositories, pluginRepositories);
            return new ModernRepositories(system, session, repositories, pluginRepositories);
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

    private static final org.apache.maven.model.Repository CENTRAL;

    static {
        org.apache.maven.model.RepositoryPolicy release = new org.apache.maven.model.RepositoryPolicy();
        org.apache.maven.model.RepositoryPolicy snapshot = new org.apache.maven.model.RepositoryPolicy();

        release.setEnabled("true");
        release.setUpdatePolicy("daily");
        release.setChecksumPolicy("warn");
        snapshot.setEnabled("false");
        CENTRAL = new org.apache.maven.model.Repository();
        CENTRAL.setId(org.apache.maven.repository.RepositorySystem.DEFAULT_REMOTE_REPO_ID);
        CENTRAL.setUrl(org.apache.maven.repository.RepositorySystem.DEFAULT_REMOTE_REPO_URL);
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
}
