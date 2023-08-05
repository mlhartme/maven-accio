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
        Repositories repositories = Repositories.create(
                localRepository, globalSettings, userSettings, allowExtensions, allowPomRepositories,
                transferListener, repositoryListener);
        return new Maven(repositories);
    }
}
