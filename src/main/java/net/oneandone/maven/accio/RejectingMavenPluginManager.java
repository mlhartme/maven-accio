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
    private final String logPrefix;

    private List<String> allowUrls;

    public RejectingMavenPluginManager() {
        super();
        this.logPrefix = getClass().getSimpleName() + ":";
        this.allowUrls = new ArrayList<>();
        addAllowProperty();
        // TODO
        System.out.println(logPrefix + "initialized, allow " + allowUrls);
    }

    public void addAllowProperty() {
        String property = System.getProperty(getClass().getName() + ":allow");
        if (property != null) {
            for (String entry : property.split(",")) {
                if (!entry.isBlank()) {
                    allowUrls.add(entry.trim());
                }
            }
        }
    }

    public void allow(String url) {
        allowUrls.add(url);
    }

    @Override
    public ExtensionRealmCache.CacheRecord setupExtensionsRealm(
            MavenProject project, Plugin plugin, RepositorySystemSession session) throws PluginManagerException {
        // getPluginRepositories normally yields the effective pom repositories AND the repositories from the effective settings!
        // but at this point in parsing, we don't have the effective pom yet. So the DefaultMavenPluginManager uses getRemotePluginRepositories
        for (var repo : project.getRemotePluginRepositories()) {
            if (!allowUrls.contains(repo.getUrl())) {
                throw new IllegalArgumentException("repository url rejected: " + repo.getUrl());
            }
        }
        return super.setupExtensionsRealm(project, plugin, session);
    }

}
