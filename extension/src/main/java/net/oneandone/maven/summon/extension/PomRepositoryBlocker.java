package net.oneandone.maven.summon.extension;

import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Repository;
import org.apache.maven.project.DefaultProjectBuildingHelper;
import org.apache.maven.project.ProjectBuildingHelper;
import org.apache.maven.project.ProjectBuildingRequest;
import org.codehaus.plexus.component.annotations.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Blocks repositories from actually being used for dependency or plugin resolution.
 * Note that they are not remove from the model, so help:effective-pom still shows them,
 * but the are skipped in the repository list actually use for resolving.
 * Does not distinguish between normal and plugin repositories because they are usually not
 * distinguished when deploying and they share the same local repository, so it's likely possible
 * to sneak plugin artifacts in by first resolving a normal artifact.
 * */
@Component(role = ProjectBuildingHelper.class)
public class PomRepositoryBlocker extends DefaultProjectBuildingHelper {
    private List<String> allowUrls;
    private final List<Repository> allowedRepositories;
    private final List<Repository> blockedRepositories;

    public PomRepositoryBlocker() {
        this.allowUrls = new ArrayList<>();
        this.allowedRepositories = new ArrayList<>();
        this.blockedRepositories = new ArrayList<>();
        addAllowProperty();
        allowUrls.add("https://repo.maven.apache.org/maven2"); // TODO
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
    public List<ArtifactRepository> createArtifactRepositories(
            List<Repository> pomRepositories, List<ArtifactRepository> externalRepositories, ProjectBuildingRequest request) throws InvalidRepositoryException {
        List<Repository> filtered;

        filtered = new ArrayList<>();
        for (var repo : pomRepositories) {
            if (allowUrls.contains(repo.getUrl())) {
                allowedRepositories.add(repo);
                filtered.add(repo);
                // System.out.println("pom repository allowed: " + repo.getUrl());
            } else {
                blockedRepositories.add(repo);
                System.out.println("pom repository blocked: " + repo.getUrl() + "\nAllowed: " + allowUrls);
            }
        }
        return super.createArtifactRepositories(filtered, externalRepositories, request);
    }
}
