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

import net.oneandone.maven.summon.extension.ExtensionBlocker;
import net.oneandone.maven.summon.extension.PomRepositoryBlocker;
import org.apache.maven.classrealm.ClassRealmManager;
import org.apache.maven.project.DefaultProjectBuilder;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingHelper;
import org.apache.maven.settings.Settings;
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
import org.eclipse.aether.RepositoryListener;
import org.eclipse.aether.transfer.TransferListener;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents configuration with settings and local configuration.
 * Build for Maven object.
 */
public class Config {
    private File localRepository;
    private File globalSettings;
    private File userSettings;
    private List<String> allowExtensions;
    private List<String> allowPomRepositories;
    private TransferListener transferListener;
    private RepositoryListener repositoryListener;

    public Config() {
        localRepository = null;
        globalSettings = null;
        userSettings = null;
        allowExtensions = new ArrayList<>();
        allowPomRepositories = new ArrayList<>();
        transferListener = null;
        repositoryListener = null;
    }

    public Config localRepository(File theLocalRepository) {
        this.localRepository = theLocalRepository;
        return this;
    }

    public Config globalSettings(File theGlobalSettings) {
        this.globalSettings = theGlobalSettings;
        return this;
    }

    public Config userSettings(File theUserSettings) {
        this.userSettings = theUserSettings;
        return this;
    }

    public Config transferListener(TransferListener theTransferListener) {
        this.transferListener = theTransferListener;
        return this;
    }

    public Config repositoryListener(RepositoryListener theRepositoryListener) {
        this.repositoryListener = theRepositoryListener;
        return this;
    }

    public Config allowExtension(String groupArtifact) {
        allowExtensions.add(groupArtifact);
        return this;
    }
    public Config allowPomRepository(String url) {
        allowPomRepositories.add(url);
        return this;
    }

    public Maven build() throws IOException {
        PlexusContainer container = createContainer();
        Settings settings = loadSettings(container, globalSettings, userSettings);
        DefaultProjectBuilder projectBuilder;
        try {

            ExtensionBlocker rm = (ExtensionBlocker) container.lookup(ClassRealmManager.class);
            if (allowExtensions != null) {
                rm.getAllowArtifacts().addAll(allowExtensions);
            }
            PomRepositoryBlocker pm = (PomRepositoryBlocker) container.lookup(ProjectBuildingHelper.class);
            if (allowPomRepositories != null) {
                for (String url : allowPomRepositories) {
                    pm.allow(url);
                }
            }
            projectBuilder = (DefaultProjectBuilder) container.lookup(ProjectBuilder.class);
        } catch (ComponentLookupException e) {
            throw new IllegalStateException(e);
        }

        ModernRepositories repositories = ModernRepositories.create(container, settings,
                localRepository, transferListener, repositoryListener);
        LegacyRepositories legacy = LegacyRepositories.create(repositories);
        return new Maven(container, repositories.repositorySystem(), repositories.repositorySession(),
                repositories.repositories(), legacy, projectBuilder);
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
    public static Settings loadSettings(PlexusContainer container, File globalSettings, File userSettings)
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
