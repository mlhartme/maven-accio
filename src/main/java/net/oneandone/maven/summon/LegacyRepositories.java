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
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.AuthenticationContext;
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
                toLegacy(config.repositorySession(), localRepo(config.repositorySession().getLocalRepository().getBasedir())),
                toLegacyList(config.repositorySession(), config.repositories()),
                toLegacyList(config.repositorySession(), config.pluginRepositories()));
    }

    public static RemoteRepository localRepo(File dir) {
        return new RemoteRepository.Builder("local", null, dir.toURI().toString())
                .setReleasePolicy(localPolicy())
                .setSnapshotPolicy(localPolicy())
                .build();
    }
    private static RepositoryPolicy localPolicy() {
        return new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_ALWAYS, RepositoryPolicy.CHECKSUM_POLICY_IGNORE);
    }

    public static ArtifactRepository toLegacy(RepositorySystemSession session, RemoteRepository repo) {
        MavenArtifactRepository result = new MavenArtifactRepository(
                repo.getId(), repo.getUrl(), toLayout(repo),
                toPolicy(repo.getPolicy(true)), toPolicy(repo.getPolicy(false)));
        result.setBlacklisted(repo.isBlocked());
        result.setAuthentication(toAuthentication(session, repo));
        result.setMirroredRepositories(toLegacyList(session, repo.getMirroredRepositories()));
        result.setProxy(toProxy(session, repo));
        return result;
    }

    public static List<ArtifactRepository> toLegacyList(RepositorySystemSession session, List<RemoteRepository> lst) {
        return lst.stream().map(repo -> toLegacy(session, repo)).toList();
    }

    public static ArtifactRepositoryLayout toLayout(RemoteRepository repo) {
        String type = repo.getContentType();
        if (type == null) {
            type = "";
        }
        return switch (type) {
            case "", "default" -> new DefaultRepositoryLayout();
            default -> throw new IllegalStateException("unknown type: " + type);
        };
    }

    private static ArtifactRepositoryPolicy toPolicy(RepositoryPolicy policy) {
        return new ArtifactRepositoryPolicy(policy.isEnabled(), policy.getUpdatePolicy(), policy.getChecksumPolicy());
    }

    private static org.apache.maven.artifact.repository.Authentication toAuthentication(RepositorySystemSession session, RemoteRepository repo) {
        org.apache.maven.artifact.repository.Authentication result;
        AuthenticationContext authCtx = repo.getProxy() == null
                ? AuthenticationContext.forRepository(session, repo)
                : AuthenticationContext.forProxy(session, repo);
        if (authCtx == null) {
            return null;
        } else {
            result = new org.apache.maven.artifact.repository.Authentication(
                    authCtx.get(AuthenticationContext.USERNAME), authCtx.get(AuthenticationContext.PASSWORD));
            result.setPrivateKey(authCtx.get(AuthenticationContext.PRIVATE_KEY_PATH));
            result.setPassphrase(authCtx.get(AuthenticationContext.PRIVATE_KEY_PASSPHRASE));
            authCtx.close();
            return result;
        }
    }

    private static org.apache.maven.repository.Proxy toProxy(RepositorySystemSession session, RemoteRepository repo) {
        org.apache.maven.repository.Proxy result = null;
        Proxy proxy = repo.getProxy();
        if (proxy != null) {
            result = new org.apache.maven.repository.Proxy();
            result.setProtocol(proxy.getType());
            result.setHost(proxy.getHost());
            result.setPort(proxy.getPort());
            var auth = toAuthentication(session, repo);
            if (auth != null) {
                result.setUserName(auth.getUsername());
                result.setPassword(auth.getPassword());
            }
        }
        return result;
    }
}
