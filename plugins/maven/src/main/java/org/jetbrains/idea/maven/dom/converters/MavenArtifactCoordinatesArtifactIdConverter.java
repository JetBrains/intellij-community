package org.jetbrains.idea.maven.dom.converters;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xml.ConvertContext;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;
import org.jetbrains.idea.maven.project.MavenId;

import java.util.Set;
import java.util.Collections;

public class MavenArtifactCoordinatesArtifactIdConverter extends MavenArtifactCoordinatesConverter {
  @Override
  protected boolean doIsValid(MavenId id, MavenProjectIndicesManager manager, ConvertContext context) {
    if (StringUtil.isEmpty(id.getGroupId()) || StringUtil.isEmpty(id.getArtifactId())) return false;
    return manager.hasArtifactId(id.getGroupId(), id.getArtifactId());
  }

  @Override
  protected Set<String> doGetVariants(MavenId id, MavenProjectIndicesManager manager) {
    if (StringUtil.isEmptyOrSpaces(id.getGroupId())) return Collections.emptySet();
    return manager.getArtifactIds(id.getGroupId());
  }
}
