package net.oneandone.maven.summon;

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

@Component(role = ClassRealmManager.class)
public class ExtensionBlocker extends DefaultClassRealmManager {
    private final Logger logger;
    private final List<String> allowGroupArtifacts;
    private final List<String> allowedExtensions;
    private final List<String> blockedExtensions;
    private final String logPrefix;

    @Inject
    public ExtensionBlocker(Logger logger, PlexusContainer container, List<ClassRealmManagerDelegate> delegates, CoreExports exports) {
        super(logger, container, delegates, exports);
        this.logger = logger;
        this.allowGroupArtifacts = new ArrayList<>();
        this.allowedExtensions = new ArrayList<>();
        this.blockedExtensions = new ArrayList<>();
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

    public List<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    public List<String> getBlockedExtensions() {
        return blockedExtensions;
    }

    @Override
    public ClassRealm createExtensionRealm(Plugin plugin, List<Artifact> artifacts) {
        String gav = plugin.getGroupId() + ":" + plugin.getArtifactId() + ":" + plugin.getVersion();
        if (allowGroupArtifacts != null && !allowGroupArtifacts.contains(plugin.getGroupId() + ":" + plugin.getArtifactId())) {
            logger.warn(logPrefix + "blocked extension " + gav);
            blockedExtensions.add(gav);
            return super.createExtensionRealm(plugin, Collections.emptyList());
        } else {
            allowedExtensions.add(gav);
            logger.info(logPrefix + "allowed extension " + gav);
            return super.createExtensionRealm(plugin, artifacts);
        }
    }
}
