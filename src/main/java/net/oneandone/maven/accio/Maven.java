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
import org.codehaus.plexus.PlexusContainer;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactType;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.graph.Dependency;
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
import org.eclipse.aether.version.Version;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class Maven implements AutoCloseable {
    public static Maven create() throws IOException {
        return new Maven(Config.create());
    }

    //--

    private final PlexusContainer container;
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

    public Maven(Config config) {
        this.container = config.container();
        this.repositorySystem = config.repositorySystem();
        this.repositorySession = config.repositorySession();
        this.builder = config.builder();
        this.remote = config.remote();
        this.remoteLegacy = config.remoteLegacy();
    }

    //--

    public File getLocalRepositoryDir() {
        return repositorySession.getLocalRepository().getBasedir();
    }

    public File getLocalRepositoryFile(Artifact artifact) {
        return new File(getLocalRepositoryDir(), repositorySession.getLocalRepositoryManager().getPathForLocalArtifact(artifact));
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
        return loadPom(resolve(new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), "pom", artifact.getVersion())));
    }

    public MavenProject loadPom(File file) throws ProjectBuildingException {
        try {
            return loadPom(file, true);
        } catch (RepositoryException e) {
            throw new IllegalStateException(e);
        }
    }

    public MavenProject loadPom(File file, boolean resolve) throws RepositoryException, ProjectBuildingException {
        return loadPom(file, resolve, resolve, null, null, null);
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

    /**
     * You'll usually pass one jar artifact and the corresponding pom artifact.
     * @param pluginName null if you deploy normal artifacts; none-null for Maven Plugins, that you wish to add a plugin mapping for;
     *                   specifies the plugin name in this case.
     */
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

    @Override
    public void close() {
       container.dispose();
    }
}
