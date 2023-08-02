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
import java.util.List;

@Component(role = ClassRealmManager.class)
public class RestrictedClassRealmManager extends DefaultClassRealmManager {
    @Inject
    public RestrictedClassRealmManager(Logger logger, PlexusContainer container, List<ClassRealmManagerDelegate> delegates, CoreExports exports) {
        super(logger, container, delegates, exports);
    }

    private List<String> allowedGroupArtifacts = null;

    public void restrict(String... restricted) {
        allowedGroupArtifacts = List.of(restricted);
    }

    @Override
    public ClassRealm createExtensionRealm(Plugin plugin, List<Artifact> artifacts) {
        if (allowedGroupArtifacts != null && !allowedGroupArtifacts.contains(plugin.getGroupId() + ":" + plugin.getArtifactId())) {
            throw new IllegalArgumentException("extension forbidden: " + plugin.getGroupId() + ":" + plugin.getArtifactId() + ":" + plugin.getVersion());
        }
        return super.createExtensionRealm(plugin, artifacts);
    }
}
