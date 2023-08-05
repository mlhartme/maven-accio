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

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingHelper;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.util.repository.AuthenticationBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Legacy configuration. This is redundant config, but we need ArtifactRepositories for the project build and
 * project building requests
 */
public record LegacyConfig(ProjectBuilder builder,
                           ArtifactRepository legacyLocal, List<ArtifactRepository> legacyRemote, List<ArtifactRepository> legacyPluginRemote) {
    public static LegacyConfig create(Config config) {
        List<ArtifactRepository> repositoriesLegacy;
        List<ArtifactRepository> pluginRepositoriesLegacy;

        try {
            repositoriesLegacy = new ArrayList<>();
            repositoriesLegacy = config.repositories().stream().map(LegacyConfig::toLegacy).toList();
            pluginRepositoriesLegacy = config.pluginRepositories().stream().map(LegacyConfig::toLegacy).toList();
            PluginRepositoryBlocker pm = (PluginRepositoryBlocker) config.container().lookup(ProjectBuildingHelper.class);
            for (var repo : pluginRepositoriesLegacy) {
                pm.allow(repo.getUrl());
            }
            return new LegacyConfig(config.container().lookup(ProjectBuilder.class),
                    localToLegacy(config.repositorySession().getLocalRepository().getBasedir()),
                    repositoriesLegacy, pluginRepositoriesLegacy);
        } catch (ComponentLookupException e) {
            throw new IllegalStateException(e);
        }
    }

    public static ArtifactRepository localToLegacy(File dir) {
        RemoteRepository tmp = new RemoteRepository.Builder("local", "typeTODO", dir.toURI().toString())
                .setReleasePolicy(new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_ALWAYS, RepositoryPolicy.CHECKSUM_POLICY_IGNORE))
                .setSnapshotPolicy(new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_ALWAYS, RepositoryPolicy.CHECKSUM_POLICY_IGNORE))
                .build();
        return toLegacy(tmp);
    }
    public static ArtifactRepository toLegacy(RemoteRepository repo) {
        MavenArtifactRepository result = new MavenArtifactRepository(
                repo.getId(), repo.getUrl(), getLayout(repo),
                policy(repo.getPolicy(true)), policy(repo.getPolicy(false)));
        /*result.setAuthentication(null); // TODO
        result.setMirroredRepositories(List.of()); // TODO
        result.setBlacklisted(false); // TODO
        result.setProxy(null); // TODO*/
        return result;
    }

    public static ArtifactRepositoryLayout getLayout(RemoteRepository repo) {
        // TODO
        return new DefaultRepositoryLayout();
    }

    private static ArtifactRepositoryPolicy policy(RepositoryPolicy policy) {
        return new ArtifactRepositoryPolicy(policy.isEnabled(), policy.getUpdatePolicy(), policy.getChecksumPolicy());
    }

    private static Authentication toAuthentication(org.apache.maven.artifact.repository.Authentication auth) {
        Authentication result = null;
        if (auth != null) {
            AuthenticationBuilder authBuilder = new AuthenticationBuilder();
            authBuilder.addUsername(auth.getUsername()).addPassword(auth.getPassword());
            authBuilder.addPrivateKey(auth.getPrivateKey(), auth.getPassphrase());
            result = authBuilder.build();
        }
        return result;
    }

    private static Proxy toProxy(org.apache.maven.repository.Proxy proxy) {
        Proxy result = null;
        if (proxy != null) {
            AuthenticationBuilder authBuilder = new AuthenticationBuilder();
            authBuilder.addUsername(proxy.getUserName()).addPassword(proxy.getPassword());
            result = new Proxy(proxy.getProtocol(), proxy.getHost(), proxy.getPort(), authBuilder.build());
        }
        return result;
    }

}
