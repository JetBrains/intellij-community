package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xml.ConvertContext;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;
import org.jetbrains.idea.maven.project.MavenId;

import java.util.Set;

public class MavenArtifactCoordinatesArtifactIdConverter extends MavenArtifactCoordinatesConverter {
  @Override
  protected boolean doIsValid(MavenId id, MavenProjectIndicesManager manager, ConvertContext context) {
    if (StringUtil.isEmpty(id.groupId) || StringUtil.isEmpty(id.artifactId)) return false;
    return manager.hasArtifactId(id.groupId, id.artifactId);
  }

  @Override
  protected Set<String> doGetVariants(MavenId id, MavenProjectIndicesManager manager) {
    return manager.getArtifactIds(id.groupId);
  }
}
