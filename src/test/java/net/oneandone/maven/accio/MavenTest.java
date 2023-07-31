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

import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.PluginManagerException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionResolutionException;
import org.eclipse.aether.version.Version;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MavenTest {
    // run this with -Xmx32m to check for memory leaks
    public static void main(String[] args) throws IOException, ProjectBuildingException {
        for (int i = 0; i < 10000; i++) {
            try (Maven maven = new Maven(Config.create())) {
                MavenProject pom;
                pom = maven.loadPom(new File("pom.xml"));
                System.out.println(i + " " + pom.getArtifact());
            }
        }
    }

    private static final Artifact JAR = new DefaultArtifact("net.oneandone:sushi:2.8.16");
    private static final Artifact WAR = new DefaultArtifact("wicket:wicket-quickstart:war:x");
    private static final Artifact NOT_FOUND = new DefaultArtifact("no.such.group:foo:x");

    private static final String MAVEN_PARENT_VERSION_PREFIX = "1.6.2-";
    private static final Artifact MAVEN_PARENT = new DefaultArtifact("de.schmizzolin.maven.poms:parent:pom:" +
            MAVEN_PARENT_VERSION_PREFIX + "SNAPSHOT");

    private Maven maven;
    private File repo;
    private File project;

    private File file(String path) {
        return new File(project, path);
    }

    @Before
    public void before() throws IOException {
        project = new File(".").getAbsoluteFile(); // TODO - multi module builds ...
        repo = new File(project, "target/repo");
        if (!repo.exists()) {
            repo.mkdirs();
        }
        maven = new Maven(Config.create(repo, null, new File(project, "src/test/settings.xml")));
    }

    @After
    public void after() throws IOException {
        maven.close();
    }

    //--

    @Test
    public void resolveRelease() throws Exception {
        assertTrue(maven.resolve(JAR).isFile());
    }

    @Test
    public void resolveSnapshot() throws Exception {
        maven.resolve(MAVEN_PARENT);
    }

    @Test(expected = ArtifactResolutionException.class)
    public void resolveNotFound() throws Exception {
        maven.resolve(NOT_FOUND);
    }

    @Test(expected = ArtifactResolutionException.class)
    public void resolveVersionNotFound() throws Exception {
        maven.resolve(JAR.setVersion("0.8.15"));
    }

    //--

    @Test
    public void loadPom() throws ProjectBuildingException {
        final String executionFromParent = "default-check maven-checkstyle-plugin [check]";
        final String executionFromDefaultLifecycle = "default-compile maven-compiler-plugin [compile]";
        MavenProject pom;
        Map<String, Object> executions;

        pom = maven.loadPom(file("pom.xml"));
        assertEquals("accio", pom.getArtifactId());

        // check that we see the effective pom
        executions = executions(pom.getModel());
        assertTrue(executions.containsKey(executionFromParent));
        assertTrue(executions.containsKey(executionFromDefaultLifecycle));
    }

    @Test
    public void loadPomWithProfiles() throws ProjectBuildingException, RepositoryException {
        final String myExec = "my-exec maven-surefire-plugin [bla]";
        final File pomFile = file("src/test/with-profile.pom");
        MavenProject pom;

        pom = maven.loadPom(pomFile.toPath().toFile());
        assertEquals("with-profile", pom.getArtifactId());
        assertFalse("contains execution from profile", executions(pom.getModel()).keySet().contains(myExec));

        pom = maven.loadPom(pomFile.toPath().toFile(), true, true, null, List.of("with-surefire"), null);
        assertTrue("contains execution from profile", executions(pom.getModel()).keySet().contains(myExec));
    }

    @Test
    public void loadPomWithProfileActivation() throws ProjectBuildingException, RepositoryException, IOException {
        final String myExec = "my-exec maven-surefire-plugin [bla]";
        final File dir = file("target/activation");
        final File marker = new File(dir, "marker");
        final File pomFile = new File(dir, "pom.xml");
        MavenProject pom;

        wipe(dir);
        Files.copy(file("src/test/with-activation.pom").toPath(), pomFile.toPath());

        assertFalse(marker.exists());
        pom = maven.loadPom(pomFile.toPath().toFile());
        assertEquals("with-activation", pom.getArtifactId());
        assertFalse("contains execution from profile", executions(pom.getModel()).keySet().contains(myExec));

        Files.writeString(marker.toPath(), "touch");
        assertTrue(marker.exists());
        pom = maven.loadPom(pomFile.toPath().toFile());
        assertEquals("with-activation", pom.getArtifactId());
        assertTrue("contains execution from profile", executions(pom.getModel()).keySet().contains(myExec));
    }

    private void wipe(File dir) {
        if (dir.isDirectory()) {
            deleteTree(dir);
        }
        dir.mkdirs();
    }

    private void deleteTree(File dir) {
        if (dir.isDirectory()) {
            for (File child : dir.listFiles()) {
                deleteTree(child);
            }
        }
        dir.delete();
    }

    private Map<String, Object> executions(Model model) {
        Map<String, Object> executions;

        executions = new LinkedHashMap<>();
        for (Plugin p : model.getBuild().getPlugins()) {
            for (PluginExecution e : p.getExecutions()) {
                executions.put(e.getId() + " " + p.getArtifactId() + " " + e.getGoals(), "" + e.getConfiguration());
            }
        }
        return executions;
    }

    @Test
    public void loadInterpolation() throws Exception {
        MavenProject pom;

        pom = maven.loadPom(file("src/test/normal.pom"));
        assertEquals("normal", pom.getName());
        assertEquals(System.getProperty("user.name"), pom.getArtifactId());
    }

    //--

    @Test
    public void availableVersions() throws VersionRangeResolutionException {
        List<Version> versions;

        versions = maven.availableVersions(JAR);
        assertEquals(1, versions.size());
        assertEquals( JAR.getVersion(), versions.get(0).toString());
    }

    @Test
    public void latestSnapshot()
            throws VersionResolutionException, VersionRangeResolutionException, ArtifactResolutionException {
        String version;

        version = maven.latestVersion(MAVEN_PARENT);
        assertTrue(version, version.startsWith(MAVEN_PARENT_VERSION_PREFIX));
        assertTrue(maven.resolve(MAVEN_PARENT.setVersion(version)).isFile());
    }

    @Test
    public void latestVersionSnapshot() throws Exception {
        Artifact artifact;
        String latest;
        File file;

        latest = maven.latestVersion(MAVEN_PARENT);
        assertNotNull(latest);
        assertTrue(latest, latest.startsWith(MAVEN_PARENT_VERSION_PREFIX));
        artifact = MAVEN_PARENT.setVersion(latest);
        file = maven.resolve(artifact);
        assertTrue(file.isFile());
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

        str = maven.nextVersion(MAVEN_PARENT);
        assertTrue(str, str.startsWith(MAVEN_PARENT_VERSION_PREFIX));
        assertEquals(str, maven.nextVersion(MAVEN_PARENT.setVersion(str)));
    }

    @Test
    public void nextVersionTimestamp() throws Exception {
        String snapshot = "3.0.0-20140310.130027-1";
        String str;

        str = maven.nextVersion(MAVEN_PARENT.setVersion(snapshot));
        assertFalse(str, snapshot.equals(str));
        assertEquals(str, maven.nextVersion(MAVEN_PARENT.setVersion(str)));
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
    public void split() {
        assertEquals(List.of("a", "b", "", "c"), List.of("a b  c".split(" ")));
        assertEquals(List.of("ab", "cd ", "", "efg"), List.of("ab:cd ::efg".split(":")));
    }

    @Test
    public void fromSettings() throws IOException {
        assertNotNull(Maven.create());
    }


    //-- extensions

    @Test
    public void pluginExtensionRejected() {
        try {
            maven.loadPom(file("src/test/with-plugin-extension.pom"));
            fail();
        } catch (ProjectBuildingException e) {
            if (e.getCause() instanceof ModelBuildingException me) {
                assertTrue(me.toString(), me.toString().contains("extension forbidden"));
            } else {
                fail(e.toString());
            }
        }
    }

    @Test
    public void pluginExtensionAllowed() throws IOException, ProjectBuildingException {
        Maven m = new Maven(Config.create(null, null, null, "org.apache.felix:maven-bundle-plugin"));
        MavenProject pom = m.loadPom(file("src/test/with-plugin-extension.pom"));
        assertEquals("true", pom.getModel().getBuild().getPluginsAsMap().get("org.apache.felix:maven-bundle-plugin").getExtensions());
    }

    @Test
    public void buildExtensionRejected() {
        try {
            maven.loadPom(file("src/test/with-build-extension.pom"));
            fail();
        } catch (ProjectBuildingException e) {
            if (e.getCause() instanceof ModelBuildingException me) {
                assertTrue(me.toString(), me.toString().contains("extension forbidden"));
            } else {
                fail(e.toString());
            }
        }
    }

    @Test
    public void buildExtensionAllowed() throws IOException, ProjectBuildingException {
        Maven m = new Maven(Config.create(null, null, null, "org.apache.felix:maven-bundle-plugin"));
        MavenProject pom = m.loadPom(file("src/test/with-build-extension.pom"));
        assertEquals(1, pom.getModel().getBuild().getExtensions().size());
    }
}
