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
package net.oneandone.maven.summon.api;

import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.codehaus.plexus.PlexusContainer;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactType;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.internal.impl.DefaultRepositorySystem;
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
import java.util.Date;
import java.util.List;
import java.util.Properties;

public class Maven implements AutoCloseable {
    /** Creates an instance with standard configuration. Use Config class to create an customited instance. */
    public static Maven create() throws IOException {
        return new Config().build();
    }

    //--

    private final PlexusContainer container;
    private final DefaultRepositorySystem repositorySystem;
    private final RepositorySystemSession repositorySession;

    /**
     * Used to resolve artifacts. With a standard configuration, this is the list of repositories
     * defined in settings.
     */
    private final List<RemoteRepository> remote;

    private final LegacyRepositories legacy;

    // This is the ProjectBuilder used by Maven 3.9.3 to load poms. Note that the respective ProjectBuilderRequest uses
    // the deprecated org.apache.maven.artifact.repository.ArtifactRepository class, so deprecation warnings are unavoidable.
    private final ProjectBuilder projectBuilder;

    //--

    public Maven(PlexusContainer container,
                 DefaultRepositorySystem repositorySystem, RepositorySystemSession repositorySession,
                 List<RemoteRepository> remote, LegacyRepositories legacy, ProjectBuilder projectBuilder) {
        this.container = container;
        this.repositorySystem = repositorySystem;
        this.repositorySession = repositorySession;
        this.remote = remote;
        this.legacy = legacy;
        this.projectBuilder = projectBuilder;
    }


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
            throw new ArtifactResolutionException(new ArrayList<>()); // TODO
        }
        return result.getArtifact().getFile();
    }

    //-- load poms

    public MavenProject loadPom(Artifact artifact) throws RepositoryException, ProjectBuildingException {
        return loadPom(resolve(new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), "pom", artifact.getVersion())));
    }

    public MavenProject loadPom(File file) throws ProjectBuildingException {
        return loadPom(file, true);
    }

    public MavenProject loadPom(File file, boolean resolve) throws ProjectBuildingException {
        return loadPom(file, resolve, null, null);
    }

    /**
     * @param userProperties may be null
     * @param profiles specifies profile to explicitly enable, may be null */
    public MavenProject loadPom(File file, boolean resolve, Properties userProperties, List<String> profiles)
            throws ProjectBuildingException {
        return loadAllPoms(false, file, resolve, userProperties, profiles).get(0);
    }

    public List<MavenProject> loadAllPoms(boolean recursive, File file, boolean resolve, Properties userProperties, List<String> profiles)
            throws ProjectBuildingException {
        DefaultProjectBuildingRequest request;
        List<ProjectBuildingResult> resultList;
        List<MavenProject> pomList;

        // from DefaultMavenExecutionRequest.getProjectBuildingRequest()
        request = new DefaultProjectBuildingRequest();
        request.setLocalRepository(legacy.local());
        request.setSystemProperties(System.getProperties());
        if (userProperties != null) {
            request.setUserProperties(userProperties);
        }
        request.setRemoteRepositories(legacy.repositories());
        request.setPluginArtifactRepositories(legacy.pluginRepositories());
        if (profiles != null) {
            request.setActiveProfileIds(profiles);
        }
        request.setProcessPlugins(true);
        request.setBuildStartTime(new Date());

        // from MavenSession.getProjectBuildingRequest
        request.setRepositorySession(repositorySession);

        // TODO
        request.setResolveDependencies(resolve);

        // settings repositories have precedence over pom repositories
        // TODO: Maven documentation claims external repositories have precedence but my reading
        // of the source is the opposite
        request.setRepositoryMerging(ProjectBuildingRequest.RepositoryMerging.REQUEST_DOMINANT);

        resultList = projectBuilder.build(List.of(file), recursive, request);

        pomList = new ArrayList<>();
        for (ProjectBuildingResult result : resultList) {
            pomList.add(result.getProject());
        }
        return pomList;
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
        deploy(target, Arrays.asList(artifacts));
    }

    /**
     * You'll usually pass one jar artifact and the corresponding pom artifact.
     */
    public void deploy(RemoteRepository target, List<Artifact> artifacts) throws DeploymentException {
        DeployRequest request;

        request = new DeployRequest();
        for (Artifact artifact : artifacts) {
            if (artifact.getFile() == null) {
                throw new IllegalArgumentException(artifact + " without file");
            }
            request.addArtifact(artifact);
        }
        request.setRepository(target);
        repositorySystem.deploy(repositorySession, request);
    }

    @Override
    public void close() {
       container.dispose();
    }
}
