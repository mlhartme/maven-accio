package net.oneandone.maven.accio;

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
public class RestrictedClassRealmManager extends DefaultClassRealmManager {
    private final Logger logger;
    private List<String> allowedGroupArtifacts;
    private final List<String> blockedExtensions;

    @Inject
    public RestrictedClassRealmManager(Logger logger, PlexusContainer container, List<ClassRealmManagerDelegate> delegates, CoreExports exports) {
        super(logger, container, delegates, exports);
        this.logger = logger;
        this.allowedGroupArtifacts = null;
        this.blockedExtensions = new ArrayList<>();
    }

    public void restrict(String... restricted) {
        allowedGroupArtifacts = List.of(restricted);
    }

    public List<String> getBlockedExtensions() {
        return blockedExtensions;
    }

    @Override
    public ClassRealm createExtensionRealm(Plugin plugin, List<Artifact> artifacts) {
        String gav = plugin.getGroupId() + ":" + plugin.getArtifactId() + ":" + plugin.getVersion();
        if (allowedGroupArtifacts != null && !allowedGroupArtifacts.contains(plugin.getGroupId() + ":" + plugin.getArtifactId())) {
            logger.warn("createExtensionRealm(" + plugin + ", WITHOUT_ARTIFACTS)");
            blockedExtensions.add(gav);
            return super.createExtensionRealm(plugin, Collections.emptyList());
        } else {
            logger.info("createExtensionRealm(" + plugin + "," + artifacts + ")");
            return super.createExtensionRealm(plugin, artifacts);
        }
    }
}
