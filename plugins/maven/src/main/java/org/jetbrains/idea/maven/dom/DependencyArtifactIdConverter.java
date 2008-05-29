package org.jetbrains.idea.maven.dom;

import org.jetbrains.idea.maven.repository.MavenIndexException;
import org.jetbrains.idea.maven.repository.MavenIndicesManager;
import org.jetbrains.idea.maven.core.util.MavenId;

import java.util.Set;

public class DependencyArtifactIdConverter extends DependencyConverter {
  @Override
  protected boolean isValid(MavenIndicesManager manager, MavenId dependencyId) throws MavenIndexException {
    return manager.hasArtifactId(dependencyId.groupId, dependencyId.artifactId);
  }

  @Override
  protected Set<String> getVariants(MavenIndicesManager manager, MavenId dependencyId) throws MavenIndexException {
    return manager.getArtifactIds(dependencyId.groupId);
  }
}
