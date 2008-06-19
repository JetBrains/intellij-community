package org.jetbrains.idea.maven.dom;

import org.jetbrains.idea.maven.repository.MavenIndexException;
import org.jetbrains.idea.maven.repository.MavenIndicesManager;
import org.jetbrains.idea.maven.core.util.MavenId;

import java.util.Set;

public class MavenGroupIdConverter extends MavenArtifactConverter {
  @Override
  protected boolean isValid(MavenIndicesManager manager, MavenId id, String specifiedPath) throws MavenIndexException {
    return manager.hasGroupId(id.groupId);
  }

  @Override
  protected Set<String> getVariants(MavenIndicesManager manager, MavenId id) throws MavenIndexException {
    return manager.getGroupIds();
  }
}
