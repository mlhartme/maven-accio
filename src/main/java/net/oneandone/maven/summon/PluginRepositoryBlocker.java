package net.oneandone.maven.summon;

import org.apache.maven.model.Model;
import org.apache.maven.plugin.PluginManagerException;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.version.PluginVersionResolutionException;
import org.apache.maven.project.DefaultProjectBuildingHelper;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingHelper;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectRealmCache;
import org.codehaus.plexus.component.annotations.Component;

import java.util.ArrayList;
import java.util.List;

@Component(role = ProjectBuildingHelper.class)
public class PluginRepositoryBlocker extends DefaultProjectBuildingHelper {
    private List<String> allowUrls;

    public PluginRepositoryBlocker() {
        this.allowUrls = new ArrayList<>();
        addAllowProperty();
        // TODO: add a "created" log statement here, but I was unable to get an logger injected ...

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
    public synchronized ProjectRealmCache.CacheRecord createProjectRealm(
            MavenProject project, Model model, ProjectBuildingRequest request)
            throws PluginResolutionException, PluginVersionResolutionException, PluginManagerException {
        for (var repo : project.getRemotePluginRepositories()) {
            if (!allowUrls.contains(repo.getUrl())) {
                throw new IllegalArgumentException("repository url rejected: " + repo.getUrl() + "\nAllowed: " + allowUrls);
            }
        }
        return super.createProjectRealm(project, model, request);
    }
}
