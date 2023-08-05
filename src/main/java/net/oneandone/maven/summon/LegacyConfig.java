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
package net.oneandone.maven.summon;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingHelper;
import org.apache.maven.repository.legacy.LegacyRepositorySystem;
import org.apache.maven.settings.Profile;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.SettingsUtils;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.aether.repository.RemoteRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Legacy configuration. This is redundant config, but we need ArtifactRepositories for the project build and
 * project building requests
 */
public record LegacyConfig(ProjectBuilder builder,
                           List<RemoteRepository> remote,
                           ArtifactRepository legacyLocal, List<ArtifactRepository> legacyRemote, List<ArtifactRepository> legacyPluginRemote) {
    public static LegacyConfig create(Config config) {
        LegacyRepositorySystem legacySystem;
        List<ArtifactRepository> repositoriesLegacy;
        List<ArtifactRepository> pluginRepositoriesLegacy;

        try {
            legacySystem = (LegacyRepositorySystem) config.container().lookup(org.apache.maven.repository.RepositorySystem.class, "default");
            repositoriesLegacy = new ArrayList<>();
            pluginRepositoriesLegacy = new ArrayList<>();
            repositoriesLegacy(legacySystem, config.settings(), repositoriesLegacy, pluginRepositoriesLegacy);
            legacySystem.injectAuthentication(config.repositorySession(), repositoriesLegacy);
            legacySystem.injectMirror(config.repositorySession(), repositoriesLegacy);
            legacySystem.injectProxy(config.repositorySession(), repositoriesLegacy);
            PluginRepositoryBlocker pm = (PluginRepositoryBlocker) config.container().lookup(ProjectBuildingHelper.class);
            for (var repo : pluginRepositoriesLegacy) {
                pm.allow(repo.getUrl());
            }
            return new LegacyConfig(config.container().lookup(ProjectBuilder.class), RepositoryUtils.toRepos(repositoriesLegacy),
                    legacySystem.createLocalRepository(config.repositorySession().getLocalRepository().getBasedir()), repositoriesLegacy, pluginRepositoriesLegacy);
        } catch (InvalidRepositoryException | ComponentLookupException e) {
            throw new IllegalStateException(e);
        }
    }

    //--

    private static void repositoriesLegacy(LegacyRepositorySystem legacy, Settings settings,
                                           List<ArtifactRepository> resultRepositories, List<ArtifactRepository> resultPluginRepositories)
            throws InvalidRepositoryException {
        boolean central;
        boolean pluginCentral;
        List<String> actives;

        central = false;
        pluginCentral = false;
        actives = settings.getActiveProfiles();
        for (Profile profile : settings.getProfiles()) {
            if (actives.contains(profile.getId()) || (profile.getActivation() != null && profile.getActivation().isActiveByDefault())) {
                var p = SettingsUtils.convertFromSettingsProfile(profile);
                central = convert(legacy, p.getRepositories(), resultRepositories);
                pluginCentral = convert(legacy, p.getPluginRepositories(), resultPluginRepositories);
            }
        }
        /* Maven defines the default central repository in its master parent - and not in the default settings, which I'd prefer.
           As a consequent, central is not always defined when loading the settings.
           I first added central to repositories only, because legacy repositories are used to load poms which ultimatly load the
           master parent with it's repository definition. However, the parent might have to be loaded from central, so repositories
           also need a central definition. */
        if (!central) {
            resultRepositories.add(legacy.createDefaultRemoteRepository());
        }
        if (!pluginCentral) {
            resultPluginRepositories.add(legacy.createDefaultRemoteRepository());
        }
    }

    /** @return true if contral was included */
    private static boolean convert(LegacyRepositorySystem legacy,  List<org.apache.maven.model.Repository> src, List<ArtifactRepository> dest) throws InvalidRepositoryException {
        boolean central;
        ArtifactRepository artifactRepository;

        central = false;
        for (org.apache.maven.model.Repository repository : src) {
            artifactRepository = legacy.buildArtifactRepository(repository);
            if ("central".equals(artifactRepository.getId())) {
                central = true;
            }
            dest.add(artifactRepository);
        }
        return central;
    }
}
