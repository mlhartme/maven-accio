package net.oneandone.maven.accio;

import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.ExtensionRealmCache;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.PluginManagerException;
import org.apache.maven.plugin.internal.DefaultMavenPluginManager;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.aether.RepositorySystemSession;

import java.util.ArrayList;
import java.util.List;

@Component(role = MavenPluginManager.class)
public class RejectingMavenPluginManager extends DefaultMavenPluginManager {
    private List<String> allowed = new ArrayList<>();

    public void allow(String url) {
        allowed.add(url);
    }
    @Override
    public ExtensionRealmCache.CacheRecord setupExtensionsRealm(
            MavenProject project, Plugin plugin, RepositorySystemSession session) throws PluginManagerException {
        for (var repo : project.getPluginRepositories()) {
            if (!allowed.contains(repo.getUrl())) {
                throw new IllegalArgumentException("repository url rejected: " + repo.getUrl());
            }
        }
        return super.setupExtensionsRealm(project, plugin, session);
    }

}
