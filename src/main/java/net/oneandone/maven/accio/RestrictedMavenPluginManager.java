package net.oneandone.maven.accio;

import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.ExtensionRealmCache;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.PluginManagerException;
import org.apache.maven.plugin.internal.DefaultMavenPluginManager;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.aether.RepositorySystemSession;

import java.util.List;


@Component(
        role = MavenPluginManager.class
)
public class RestrictedMavenPluginManager extends DefaultMavenPluginManager {
    private List<String> allowedGroupArtifacts;
    public RestrictedMavenPluginManager() {
        this.allowedGroupArtifacts = null;
    }

    public void restrict(String... restricted) {
        allowedGroupArtifacts = List.of(restricted);
    }

    public ExtensionRealmCache.CacheRecord setupExtensionsRealm(MavenProject project, Plugin plugin, RepositorySystemSession session) throws PluginManagerException {
        if (allowedGroupArtifacts != null && !allowedGroupArtifacts.contains(plugin.getGroupId() + ":" + plugin.getArtifactId())) {
            throw new PluginManagerException(plugin, "extension forbidden: " + plugin.getGroupId() + ":"
                    + plugin.getArtifactId() + ":" + plugin.getVersion(), (Throwable) null);
        }
        return super.setupExtensionsRealm(project, plugin, session);
    }
}
