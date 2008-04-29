package org.jetbrains.idea.maven.dom;

import org.jetbrains.idea.maven.repository.MavenRepositoryException;
import org.jetbrains.idea.maven.repository.MavenRepositoryManager;

import java.util.Set;

public class DependencyGroupIdConverter extends DependencyConverter {
  protected Set<String> getVariants(MavenRepositoryManager manager, String groupId, String artifactId) throws MavenRepositoryException {
    return manager.getGroupIds();
  }
}
