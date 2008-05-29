package org.jetbrains.idea.maven.dom;

import org.jetbrains.idea.maven.repository.MavenIndexException;
import org.jetbrains.idea.maven.repository.MavenIndicesManager;
import org.jetbrains.idea.maven.core.util.MavenId;

import java.util.Set;

public class DependencyGroupIdConverter extends DependencyConverter {
  @Override
  protected boolean isValid(MavenIndicesManager manager, MavenId dependencyId) throws MavenIndexException {
    return manager.hasGroupId(dependencyId.groupId);
  }

  @Override
  protected Set<String> getVariants(MavenIndicesManager manager, MavenId dependencyId) throws MavenIndexException {
    return manager.getGroupIds();
  }
}
