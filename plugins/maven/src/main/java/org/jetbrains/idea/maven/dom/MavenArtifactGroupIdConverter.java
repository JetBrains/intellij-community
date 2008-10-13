package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xml.ConvertContext;
import org.jetbrains.idea.maven.utils.MavenId;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;

import java.util.Set;

public class MavenArtifactGroupIdConverter extends MavenArtifactConverter {
  @Override
  protected boolean isValid(Project project, MavenProjectIndicesManager manager, MavenId id, ConvertContext context) {
    if (StringUtil.isEmpty(id.groupId)) return false;
    return manager.hasGroupId(id.groupId);
  }

  @Override
  protected Set<String> getVariants(Project project, MavenProjectIndicesManager manager, MavenId id, ConvertContext context) {
    return manager.getGroupIds();
  }
}
