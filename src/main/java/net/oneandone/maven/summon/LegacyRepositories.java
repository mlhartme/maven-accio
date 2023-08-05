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
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;

import java.io.File;
import java.util.List;

/**
 * Legacy repositories. This is redundant config, but we need ArtifactRepositories for the project builder and
 * project building requests
 */
public record LegacyRepositories(
        ArtifactRepository local, List<ArtifactRepository> repositories, List<ArtifactRepository> pluginRepositories) {
    public static LegacyRepositories create(Config config) {
        return new LegacyRepositories(
                toLegacy(localRepo(config.repositorySession().getLocalRepository().getBasedir())),
                toLegacyList(config.repositories()),
                toLegacyList(config.pluginRepositories()));
    }

    public static RemoteRepository localRepo(File dir) {
        return new RemoteRepository.Builder("local", "typeTODO", dir.toURI().toString())
                .setReleasePolicy(localPolicy())
                .setSnapshotPolicy(localPolicy())
                .build();
    }
    private static RepositoryPolicy localPolicy() {
        return new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_ALWAYS, RepositoryPolicy.CHECKSUM_POLICY_IGNORE);
    }

    public static ArtifactRepository toLegacy(RemoteRepository repo) {
        MavenArtifactRepository result = new MavenArtifactRepository(
                repo.getId(), repo.getUrl(), getLayout(repo),
                policy(repo.getPolicy(true)), policy(repo.getPolicy(false)));
        result.setBlacklisted(repo.isBlocked());
        result.setAuthentication(toAuthentication(repo.getAuthentication()));
        result.setMirroredRepositories(toLegacyList(repo.getMirroredRepositories()));
        result.setProxy(toProxy(repo.getProxy()));
        return result;
    }

    public static List<ArtifactRepository> toLegacyList(List<RemoteRepository> lst) {
        return lst.stream().map(LegacyRepositories::toLegacy).toList();
    }

    public static ArtifactRepositoryLayout getLayout(RemoteRepository repo) {
        // TODO
        return new DefaultRepositoryLayout();
    }

    private static ArtifactRepositoryPolicy policy(RepositoryPolicy policy) {
        return new ArtifactRepositoryPolicy(policy.isEnabled(), policy.getUpdatePolicy(), policy.getChecksumPolicy());
    }

    private static org.apache.maven.artifact.repository.Authentication toAuthentication(Authentication auth) {
        org.apache.maven.artifact.repository.Authentication result = null;
        if (auth != null) {
            // TODO
            throw new UnsupportedOperationException(auth.getClass().toString());
        }
        return result;
    }

    private static org.apache.maven.repository.Proxy toProxy(Proxy proxy) {
        org.apache.maven.repository.Proxy result = null;
        if (proxy != null) {
            result = new org.apache.maven.repository.Proxy();
            result.setProtocol(proxy.getType());
            result.setHost(proxy.getHost());
            result.setPort(proxy.getPort());
            var auth = toAuthentication(proxy.getAuthentication());
            if (auth != null) {
                result.setUserName(auth.getUsername());
                result.setPassword(auth.getPassword());
            }
        }
        return result;
    }
}
