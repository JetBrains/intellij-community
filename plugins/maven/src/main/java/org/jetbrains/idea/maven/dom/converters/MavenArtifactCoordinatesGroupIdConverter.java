package org.jetbrains.idea.maven.dom.converters;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.hash.HashSet;
import com.intellij.util.xml.ConvertContext;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;
import org.jetbrains.idea.maven.model.MavenId;

import java.util.Collection;
import java.util.Set;

public class MavenArtifactCoordinatesGroupIdConverter extends MavenArtifactCoordinatesConverter {
  @Override
  protected boolean doIsValid(MavenId id, MavenProjectIndicesManager manager, ConvertContext context) {
    if (StringUtil.isEmpty(id.getGroupId())) return false;
    return manager.hasGroupId(id.getGroupId());
  }

  @Override
  protected Set<String> doGetVariants(MavenId id, MavenProjectIndicesManager manager) {
    return manager.getGroupIds();
  }

  public Collection<String> getSmartVariants(ConvertContext convertContext) {
    Set<String> groupIds = new HashSet<String>();
    String artifactId = MavenArtifactCoordinatesHelper.getId(convertContext).getArtifactId();
    if (!StringUtil.isEmptyOrSpaces(artifactId)) {
      MavenProjectIndicesManager manager = MavenProjectIndicesManager.getInstance(convertContext.getFile().getProject());
      for (String grouipId : manager.getGroupIds()) {
          if (manager.getArtifactIds(grouipId).contains(artifactId)) {
            groupIds.add(grouipId);
          }
      }
    }
    return groupIds;
  }
}
