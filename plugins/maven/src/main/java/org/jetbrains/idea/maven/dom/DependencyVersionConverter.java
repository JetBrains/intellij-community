package org.jetbrains.idea.maven.dom;

import org.jetbrains.idea.maven.repository.MavenIndexException;
import org.jetbrains.idea.maven.repository.MavenIndicesManager;

import java.util.Set;

public class DependencyVersionConverter extends DependencyConverter {
  protected Set<String> getVariants(MavenIndicesManager manager, String groupId, String artifactId) throws MavenIndexException {
    return manager.getVersions(groupId, artifactId);
  }
}