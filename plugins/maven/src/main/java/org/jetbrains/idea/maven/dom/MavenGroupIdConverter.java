package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.project.Project;
import com.intellij.util.xml.ConvertContext;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.indices.MavenIndexException;
import org.jetbrains.idea.maven.indices.MavenIndicesManager;

import java.util.Set;

public class MavenGroupIdConverter extends MavenArtifactConverter {
  @Override
  protected boolean isValid(Project project, MavenIndicesManager manager, MavenId id, ConvertContext context) throws MavenIndexException {
    return manager.hasGroupId(project, id.groupId);
  }

  @Override
  protected Set<String> getVariants(Project project, MavenIndicesManager manager, MavenId id, ConvertContext context) throws MavenIndexException {
    return manager.getGroupIds(project);
  }
}
