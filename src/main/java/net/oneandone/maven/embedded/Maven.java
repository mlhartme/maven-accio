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
package net.oneandone.maven.embedded;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.metadata.GroupRepositoryMetadata;
import org.apache.maven.artifact.repository.metadata.MetadataBridge;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
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
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositoryListener;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactType;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationSelector;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.ProxySelector;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.resolution.VersionRequest;
import org.eclipse.aether.resolution.VersionResolutionException;
import org.eclipse.aether.resolution.VersionResult;
import org.eclipse.aether.transfer.TransferListener;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.eclipse.aether.util.repository.DefaultMirrorSelector;
import org.eclipse.aether.util.repository.DefaultProxySelector;
import org.eclipse.aether.version.Version;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Notes:
 * * does not load Maven extensions, neither core nor plugin nor build extensions
 *   (Maven does so in DefaultMaven
 *   https://github.com/apache/maven/blob/21122926829f1ead511c958d89bd2f672198ae9f/maven-core/src/main/java/org/apache/maven/DefaultMaven.java#L327C5-L327C5)
 * * does not setup/use EventSpyDispatcher/EventSpy, so the respective events are not fired/processed (Maven uses this for plugin validation)
 * * does not setup/use ExecutionEventCatapult/ExecutionListener (Maven does use them for console/log output)
 */
public class Maven {
    public static Maven withSettings() throws IOException {
        return withSettings(null, null, null);
    }

    /**
     * @param localRepository null to use default
     * @param globalSettings null to use default
     * @param userSettings null to use default
     */
    public static Maven withSettings(File localRepository, File globalSettings, File userSettings)
            throws IOException {
        return withSettings(localRepository, globalSettings, userSettings, container(), null, null);
    }

    public static Maven withSettings(File localRepository, File globalSettings, File userSettings,
                                     DefaultPlexusContainer container,
                                     TransferListener transferListener, RepositoryListener repositoryListener) throws IOException {
        RepositorySystem system;
        DefaultRepositorySystemSession session;
        Settings settings;
        LegacyRepositorySystem legacySystem;
        List<ArtifactRepository> repositoriesLegacy;

        try {
            try {
                settings = loadSettings(globalSettings, userSettings, container);
            } catch (SettingsBuildingException | XmlPullParserException e) {
                throw new IOException("cannot load settings: " + e.getMessage(), e);
            }
            system = container.lookup(RepositorySystem.class);
            session = createSession(transferListener, repositoryListener, system,
                    createLocalRepository(localRepository, settings), settings);
            legacySystem = (LegacyRepositorySystem) container.lookup(org.apache.maven.repository.RepositorySystem.class, "default");
            repositoriesLegacy = repositoriesLegacy(legacySystem, settings);
            legacySystem.injectAuthentication(session, repositoriesLegacy);
            legacySystem.injectMirror(session, repositoriesLegacy);
            legacySystem.injectProxy(session, repositoriesLegacy);
            return new Maven(system, session, container.lookup(ProjectBuilder.class),
                    RepositoryUtils.toRepos(repositoriesLegacy), repositoriesLegacy);
        } catch (InvalidRepositoryException | ComponentLookupException e) {
            throw new IllegalStateException(e);
        }
    }

    private static LocalRepository createLocalRepository(File localRepository, Settings settings) {
        String localRepositoryStr;
        LocalRepository localRepositoryObj;

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
        localRepositoryObj = new LocalRepository(localRepositoryStr);
        return localRepositoryObj;
    }

    private static DefaultRepositorySystemSession createSession(TransferListener transferListener, RepositoryListener repositoryListener,
                                                              RepositorySystem system, LocalRepository localRepository, Settings settings) {
        DefaultRepositorySystemSession session;
        final List<Server> servers;

        servers = settings.getServers();
        session = MavenRepositorySystemUtils.newSession();
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

    public static File userHome() {
        return new File(System.getProperty("user.home"));
    }

    public static File defaultLocalRepositoryDir() {
        return new File(userHome(), ".m2/repository");
    }

    public static DefaultPlexusContainer container() {
        return container(null, null, Logger.LEVEL_DISABLED);
    }

    public static DefaultPlexusContainer container(ClassWorld classWorld, ClassRealm realm, int loglevel) {
        DefaultContainerConfiguration config;
        DefaultPlexusContainer container;

        config = new DefaultContainerConfiguration();
        if (classWorld != null) {
            config.setClassWorld(classWorld);
        }
        if (realm != null) {
            config.setRealm(realm);
        }
        config.setAutoWiring(true);
        config.setClassPathScanning(PlexusConstants.SCANNING_INDEX);
        try {
            container = new DefaultPlexusContainer(config);
        } catch (PlexusContainerException e) {
            throw new IllegalStateException(e);
        }
        container.getLoggerManager().setThreshold(loglevel);
        return container;
    }

    //--

    private final RepositorySystem repositorySystem;
    private final DefaultRepositorySystemSession repositorySession;

    /**
     * Used to resolve artifacts.
     */
    private final List<RemoteRepository> remote;

    /** Remote repositories used to load poms. Legacy objects :( */
    private final List<ArtifactRepository> remoteLegacy;

    // This is the ProjectBuilder used by Maven 3.9.3 to load poms. Note that the respective ProjectBuilderRequest uses
    // the deprecated org.apache.maven.artifact.repository.ArtifactRepository class, so deprecation warnings are unavailable.
    private final ProjectBuilder builder;

    public Maven(RepositorySystem repositorySystem, DefaultRepositorySystemSession repositorySession, ProjectBuilder builder,
                 List<RemoteRepository> remote, List<ArtifactRepository> remoteLegacy) {
        this.repositorySystem = repositorySystem;
        this.repositorySession = repositorySession;
        this.builder = builder;
        this.remote = remote;
        this.remoteLegacy = remoteLegacy;
    }

    public RepositorySystem getRepositorySystem() {
        return repositorySystem;
    }

    public DefaultRepositorySystemSession getRepositorySession() {
        return repositorySession;
    }

    public List<ArtifactRepository> remoteLegacyRepositories() {
        return remoteLegacy;
    }

    public List<RemoteRepository> remoteResolveRepositories() {
        return remote;
    }

    public File getLocalRepositoryDir() {
        return repositorySession.getLocalRepository().getBasedir();
    }

    public File getLocalRepositoryFile(Artifact artifact) {
        return new File(getLocalRepositoryDir(), repositorySession.getLocalRepositoryManager().getPathForLocalArtifact(artifact));
    }

    public List<File> files(List<Artifact> artifacts) {
        List<File> result;

        result = new ArrayList<>();
        for (Artifact a : artifacts) {
            result.add(a.getFile());
        }
        return result;
    }

    //-- resolve

    public File resolve(String groupId, String artifactId, String version) throws ArtifactResolutionException {
        return resolve(groupId, artifactId, "jar", version);
    }

    public File resolve(String groupId, String artifactId, String extension, String version) throws ArtifactResolutionException {
        return resolve(new DefaultArtifact(groupId, artifactId, extension, version));
    }

    public File resolve(String gav) throws ArtifactResolutionException {
        return resolve(new DefaultArtifact(gav));
    }

    public File resolve(Artifact artifact) throws ArtifactResolutionException {
        return resolve(artifact, remote);
    }

    public File resolve(Artifact artifact, List<RemoteRepository> remoteRepositories) throws ArtifactResolutionException {
        ArtifactRequest request;
        ArtifactResult result;

        request = new ArtifactRequest(artifact, remoteRepositories, null);
        result = repositorySystem.resolveArtifact(repositorySession, request);
        if (!result.isResolved()) {
            throw new ArtifactResolutionException(new ArrayList<ArtifactResult>()); // TODO
        }
        return result.getArtifact().getFile();
    }

    //-- load poms

    public MavenProject loadPom(Artifact artifact) throws RepositoryException, ProjectBuildingException {
        return loadPom(
                resolve(new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), "pom", artifact.getVersion())),
                false, false);
    }

    public MavenProject loadPom(File file) throws ProjectBuildingException {
        try {
            return loadPom(file, true, true);
        } catch (RepositoryException e) {
            throw new IllegalStateException(e);
        }
    }

    public MavenProject loadPom(File file, boolean resolve, boolean processPlugins) throws RepositoryException, ProjectBuildingException {
        return loadPom(file, resolve, processPlugins, null, null, null);
    }

    /**
     * @param userProperties may be null
     * @param profiles specifies profile to explicitly enable, may be null
     * @param dependencies out argument, receives all dependencies if not null */
    public MavenProject loadPom(File file, boolean resolve, boolean processPLugins, Properties userProperties, List<String> profiles,
                                List<Dependency> dependencies) throws RepositoryException, ProjectBuildingException {
        ProjectBuildingRequest request;
        ProjectBuildingResult result;
        List<Exception> problems;

        request = new DefaultProjectBuildingRequest();
        request.setRepositorySession(repositorySession);
        request.setRemoteRepositories(remoteLegacy);
        request.setProcessPlugins(processPLugins);
        request.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
        request.setSystemProperties(System.getProperties());
        if (userProperties != null) {
            request.setUserProperties(userProperties);
        }
        // If you don't turn this into RepositoryMerging.REQUEST_DOMINANT the dependencies will be resolved against Maven Central
        // and not against the configured repositories. The default of the DefaultProjectBuildingRequest is
        // RepositoryMerging.POM_DOMINANT.
        request.setRepositoryMerging(ProjectBuildingRequest.RepositoryMerging.REQUEST_DOMINANT);
        request.setResolveDependencies(resolve);
        if (profiles != null) {
            request.setActiveProfileIds(profiles);
        }
        result = builder.build(file.toPath().toFile(), request);

        // TODO: I've seen these collection errors for a dependency with ranges. Why does Aether NOT throw an exception in this case?
        if (result.getDependencyResolutionResult() != null) {
            problems = result.getDependencyResolutionResult().getCollectionErrors();
            if (problems != null && !problems.isEmpty()) {
                throw new RepositoryException("collection errors: " + problems.toString(), problems.get(0));
            }
        }

        if (dependencies != null) {
            if (!resolve) {
                throw new IllegalArgumentException();
            }
            dependencies.addAll(result.getDependencyResolutionResult().getDependencies());
        }
        return result.getProject();
    }

    //-- versions

    /** @return Latest version field from metadata.xml of the repository that was last modified. Never null */
    public String latestVersion(Artifact artifact) throws VersionRangeResolutionException, VersionResolutionException {
        Artifact range;
        VersionRangeRequest request;
        VersionRangeResult result;
        List<Version> versions;
        String version;

        // CAUTION: do not use version "LATEST" because the respective field in metadata.xml is not set reliably:
        range = artifact.setVersion("[,]");
        request = new VersionRangeRequest(range, remote, null);
        result = repositorySystem.resolveVersionRange(repositorySession, request);
        versions = result.getVersions();
        if (versions.size() == 0) {
            throw new VersionRangeResolutionException(result, "no version found");
        }
        version = versions.get(versions.size() - 1).toString();
        if (version.endsWith("-SNAPSHOT")) {
            version = latestSnapshot(artifact.setVersion(version));
        }
        return version;
    }

    /** @return a timestamp version if a deploy artifact wins; a SNAPSHOT if a location artifact wins */
    private String latestSnapshot(Artifact artifact) throws VersionResolutionException {
        VersionRequest request;
        VersionResult result;

        request = new VersionRequest(artifact, remote, null);
        result = repositorySystem.resolveVersion(repositorySession, request);
        return result.getVersion();
    }

    public String nextVersion(Artifact artifact) throws RepositoryException {
        if (artifact.isSnapshot()) {
            return latestSnapshot(artifact.setVersion(artifact.getBaseVersion()));
        } else {
            return latestRelease(artifact);
        }
    }

    public String latestRelease(Artifact artifact) throws VersionRangeResolutionException {
        List<Version> versions;
        Version version;

        versions = availableVersions(artifact.setVersion("[" + artifact.getVersion() + ",]"));

        // ranges also return SNAPSHOTS. The release/compatibility notes say they don't, but the respective bug
        // was re-opened: http://jira.codehaus.org/browse/MNG-3092
        for (int i = versions.size() - 1; i >= 0; i--) {
            version = versions.get(i);
            if (!version.toString().endsWith("SNAPSHOT")) {
                return version.toString();
            }
        }
        return artifact.getVersion();
    }

    public List<Version> availableVersions(String groupId, String artifactId) throws VersionRangeResolutionException {
        return availableVersions(groupId, artifactId, null);
    }

    public List<Version> availableVersions(String groupId, String artifactId, ArtifactType type) throws VersionRangeResolutionException {
        return availableVersions(new DefaultArtifact(groupId, artifactId, null, null, "[0,)", type));
    }

    public List<Version> availableVersions(Artifact artifact) throws VersionRangeResolutionException {
        VersionRangeRequest request;
        VersionRangeResult rangeResult;

        request = new VersionRangeRequest(artifact, remote, null);
        rangeResult = repositorySystem.resolveVersionRange(repositorySession, request);
        return rangeResult.getVersions();
    }

    //-- deploy

    /** convenience method */
    public void deploy(RemoteRepository target, Artifact... artifacts) throws DeploymentException {
        deploy(target, null, Arrays.asList(artifacts));
    }

    /** convenience method */
    public void deploy(RemoteRepository target, String pluginName, Artifact... artifacts) throws DeploymentException {
        deploy(target, pluginName, Arrays.asList(artifacts));
    }

    // CHECKSTYLE:OFF
    /**
     * You'll usually pass one jar artifact and the corresponding pom artifact.
     * @param pluginName null if you deploy normal artifacts; none-null for Maven Plugins, that you wish to add a plugin mapping for;
     *                   specifies the plugin name in this case.
     */
    // CHECKSTYLE:ON
    public void deploy(RemoteRepository target, String pluginName, List<Artifact> artifacts) throws DeploymentException {
        DeployRequest request;
        GroupRepositoryMetadata gm;
        String prefix;

        request = new DeployRequest();
        for (Artifact artifact : artifacts) {
            if (artifact.getFile() == null) {
                throw new IllegalArgumentException(artifact.toString() + " without file");
            }
            request.addArtifact(artifact);
            if (pluginName != null) {
                gm = new GroupRepositoryMetadata(artifact.getGroupId());
                prefix = getGoalPrefixFromArtifactId(artifact.getArtifactId());
                gm.addPluginMapping(prefix, artifact.getArtifactId(), pluginName);
                request.addMetadata(new MetadataBridge(gm));
            }
        }
        request.setRepository(target);
        repositorySystem.deploy(repositorySession, request);
    }

    /** from PluginDescriptor */
    public static String getGoalPrefixFromArtifactId(String artifactId) {
        if ("maven-plugin-plugin".equals(artifactId)) {
            return "plugin";
        } else {
            return artifactId.replaceAll("-?maven-?", "").replaceAll("-?plugin-?", "");
        }
    }

    //-- utils

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
            userSettings = new File(userHome(), ".m2/settings.xml");
        }
        request.setGlobalSettingsFile(globalSettings.toPath().toFile());
        request.setUserSettingsFile(userSettings.toPath().toFile());
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
        mvn = which("mvn");
        if (mvn != null) {
            mvn = resolve(mvn);
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

    private static File which(String cmd) {
        String path;
        File file;

        path = System.getenv("PATH");
        if (path != null) {
            for (String entry : path.split(":")) {
                file = new File(entry.trim(), cmd);
                if (file.isFile()) {
                    return file;
                }
            }
        }
        return null;
    }

    private static File resolve(File originalFile) throws IOException {
        return originalFile.toPath().toRealPath().toFile();
    }

    private static List<ArtifactRepository> repositoriesLegacy(LegacyRepositorySystem legacy, Settings settings)
            throws InvalidRepositoryException {
        boolean central;
        List<ArtifactRepository> result;
        List<String> actives;
        ArtifactRepository artifactRepository;

        central = false;
        result = new ArrayList<>();
        actives = settings.getActiveProfiles();
        for (Profile profile : settings.getProfiles()) {
            if (actives.contains(profile.getId()) || (profile.getActivation() != null && profile.getActivation().isActiveByDefault())) {
                for (org.apache.maven.model.Repository repository : SettingsUtils.convertFromSettingsProfile(profile).getRepositories()) {
                    artifactRepository = legacy.buildArtifactRepository(repository);
                    if ("central".equals(artifactRepository.getId())) {
                        central = true;
                    }
                    result.add(artifactRepository);
                }
            }
        }
        if (!central) {
            /* Maven defines the default central repository in its master parent - and not in the default settings, which I'd prefer.
               As a consequent, central is not always defined when loading the settings.
               I first added central to repositories only, because legacy repositories are used to load poms which ultimatly load the
               master parent with it's repository definition. However, the parent might have to be loaded from central, so repositories
               also need a central definition. */
            result.add(legacy.createDefaultRemoteRepository());
        }
        return result;
    }
}
