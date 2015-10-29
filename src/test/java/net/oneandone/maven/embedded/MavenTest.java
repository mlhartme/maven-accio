/**
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

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Test;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionResolutionException;
import org.eclipse.aether.version.Version;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MavenTest {
    private static final Artifact JAR = new DefaultArtifact("net.oneandone:sushi:2.8.16");
    private static final Artifact WAR = new DefaultArtifact("wicket:wicket-quickstart:war:x");
    private static final Artifact NOT_FOUND = new DefaultArtifact("no.such.group:foo:x");
    private static final Artifact SNAPSHOT = new DefaultArtifact("net.oneandone.stool:main:3.3.0-SNAPSHOT");

    private World world;
    private Maven maven;

    public MavenTest() throws IOException {
        world = new World();
        maven = Maven.withSettings(world);
    }

    //--

    @Test
    public void resolveRelease() throws Exception {
        maven.resolve(JAR).checkFile();
    }

    @Test
    public void resolveSnapshot() throws Exception {
        maven.resolve(SNAPSHOT);
    }

    @Test(expected = ArtifactResolutionException.class)
    public void resolveNotFound() throws Exception {
        maven.resolve(NOT_FOUND).checkFile();
    }

    @Test(expected = ArtifactResolutionException.class)
    public void resolveVersionNotFound() throws Exception {
        maven.resolve(JAR.setVersion("0.8.15")).checkFile();
    }

    //--

    @Test
    public void loadPom() throws ArtifactResolutionException, ProjectBuildingException {
        MavenProject pom;

        pom = maven.loadPom(maven.getWorld().guessProjectHome(getClass()).join("pom.xml"));
        assertEquals("embedded", pom.getArtifactId());
    }

    @Test
    public void loadInterpolation() throws Exception {
        MavenProject pom;

        pom = maven.loadPom(world.guessProjectHome(getClass()).join("src/test/normal.pom"));
        assertEquals("normal", pom.getName());
        assertEquals(System.getProperty("user.name"), pom.getArtifactId());
    }

    //--

    @Test
    public void availableVersions() throws VersionResolutionException, VersionRangeResolutionException {
        List<Version> versions;

        versions = maven.availableVersions(JAR);
        assertEquals(1, versions.size());
        assertEquals( JAR.getVersion(), versions.get(0).toString());
    }

    @Test
    public void latestSnapshot()
            throws VersionResolutionException, VersionRangeResolutionException, ArtifactResolutionException, IOException {
        String version;

        version = maven.latestVersion(SNAPSHOT);
        assertTrue(version, version.startsWith("3.3.0-"));
        maven.resolve(SNAPSHOT.setVersion(version)).checkFile();
    }

    @Test
    public void latestVersionSnapshot() throws Exception {
        Artifact artifact;
        String latest;
        FileNode file;

        latest = maven.latestVersion(SNAPSHOT);
        assertNotNull(latest);
        assertTrue(latest, latest.startsWith("3.3.0-"));
        artifact = SNAPSHOT.setVersion(latest);
        file = maven.resolve(artifact);
        file.checkFile();
        assertTrue(file.length() > 0);
        // cannot load poms >controlpanel-wars 1.0-SNAPSHOT>controlpanel 1.0-SNAPSHOT because
        // the last pom is not deployed, not even on billy ...
        //   resolver.loadPom(artifact);
    }

    @Test(expected = VersionRangeResolutionException.class)
    public void latestVersionNotFound() throws Exception {
        maven.latestVersion(NOT_FOUND);
    }

    @Test
    public void nextVersionRelease() throws Exception {
        String current;
        String str;

        current = maven.nextVersion(WAR.setVersion("1.2.6"));
        assertTrue(current, current.startsWith("1.2.7"));
        assertFalse(current, current.endsWith("-SNAPSHOT"));
        str = maven.nextVersion(WAR.setVersion(current));
        assertEquals(current, str);
    }

    @Test
    public void nextVersionSnapshotCP() throws Exception {
        String str;

        str = maven.nextVersion(SNAPSHOT);
        assertTrue(str, str.startsWith("3.3.0-"));
        assertEquals(str, maven.nextVersion(SNAPSHOT.setVersion(str)));
    }

    @Test
    public void nextVersionTimestamp() throws Exception {
        String snapshot = "3.0.0-20140310.130027-1";
        String str;

        str = maven.nextVersion(SNAPSHOT.setVersion(snapshot));
        assertFalse(str, snapshot.equals(str));
        assertEquals(str, maven.nextVersion(SNAPSHOT.setVersion(str)));
        assertTrue(str, str.startsWith("3.0.0-"));
    }

    @Test
    public void nextVersionReleaseNotFound() throws Exception {
        assertEquals("1", maven.nextVersion(NOT_FOUND.setVersion("1")));
    }

    @Test
    public void nextVersionSnapshotNotFound() throws Exception {
        assertEquals("1-SNAPSHOT", maven.nextVersion(NOT_FOUND.setVersion("1-SNAPSHOT")));
    }

    //--

    @Test
    public void fromSettings() throws IOException {
        assertNotNull(Maven.withSettings(world));
    }
}
