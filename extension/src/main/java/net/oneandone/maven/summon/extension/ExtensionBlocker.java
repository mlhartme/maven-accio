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
package net.oneandone.maven.summon.extension;

import org.apache.maven.classrealm.ClassRealmManager;
import org.apache.maven.classrealm.ClassRealmManagerDelegate;
import org.apache.maven.classrealm.DefaultClassRealmManager;
import org.apache.maven.extension.internal.CoreExports;
import org.apache.maven.model.Plugin;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.aether.artifact.Artifact;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Blocks build- and plugin extensions from being loaded. This is a security problem because the extension code is added
 * to the classpath when loading the pom - it can override Maven components and thus inject code that's executed when
 * Maven loads the respective component.
 *
 * Context:
 * Maven manages class loading with Plexus ClassWorlds, new code is added via ClassRealms, and a ClassRealmManager controls all this.
 * ClassRealmManager has methods to create project, plugin, and extension realms. A project realm is a composite of the contained extension
 * realms. Plugin realms are used when invoking plugin.
 *
 * When loading a pom, the project realm and thus all plugin realms are created, which enables extensions to override components used
 * by Maven and thus inject code. BlockingClassRealmManager is meant to mitigate this.
 *
 * Implementation: Maven adds extensions to the class loader via ClassRealmManager.createExtensionRealm(). ExtensionBlocker wraps
 * this restrict class loading appropriately: it simply created an empty class realm for them.
 *
 * ExtensionBlocker can allow extensions by groupId+artifactId to be loaded, the version is intentionally not fixed.
 * However, attackers could use this provide their own version and make it available by configuring a plugin repository.
 * Use PomRepositoryBlocker is mitigate this.
 */
@Component(role = ClassRealmManager.class)
public class ExtensionBlocker extends DefaultClassRealmManager {
    private final Logger logger;
    private final List<String> allowGroupArtifacts;
    private final String logPrefix;

    @Inject
    public ExtensionBlocker(Logger logger, PlexusContainer container, List<ClassRealmManagerDelegate> delegates, CoreExports exports) {
        super(logger, container, delegates, exports);
        this.logger = logger;
        this.allowGroupArtifacts = new ArrayList<>();
        this.logPrefix = getClass().getSimpleName() + ": ";
        addAllowProperty();
        logger.info(logPrefix + "created, allow " + allowGroupArtifacts);
    }

    public void addAllowProperty() {
        String property = System.getProperty(getClass().getName() + ":allow");
        if (property != null) {
            for (String entry : property.split(",")) {
                if (!entry.isBlank()) {
                    allowGroupArtifacts.add(entry.trim());
                }
            }
        }
    }

    public List<String> getAllowArtifacts() {
        return allowGroupArtifacts;
    }

    @Override
    public ClassRealm createExtensionRealm(Plugin plugin, List<Artifact> artifacts) {
        String gav = plugin.getGroupId() + ":" + plugin.getArtifactId() + ":" + plugin.getVersion();
        if (allowGroupArtifacts != null && !allowGroupArtifacts.contains(plugin.getGroupId() + ":" + plugin.getArtifactId())) {
            logger.warn(logPrefix + "blocked extension " + gav);
            return super.createExtensionRealm(plugin, Collections.emptyList());
        } else {
            logger.info(logPrefix + "allowed extension " + gav);
            return super.createExtensionRealm(plugin, artifacts);
        }
    }
}
