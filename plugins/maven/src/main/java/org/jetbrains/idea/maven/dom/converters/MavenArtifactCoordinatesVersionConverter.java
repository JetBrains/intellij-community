package org.jetbrains.idea.maven.dom.converters;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xml.ConvertContext;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;
import org.jetbrains.idea.maven.project.MavenId;

import java.util.Set;

public class MavenArtifactCoordinatesVersionConverter extends MavenArtifactCoordinatesConverter {
  @Override
  protected boolean doIsValid(MavenId id, MavenProjectIndicesManager manager, ConvertContext context) {
    if (StringUtil.isEmpty(id.getGroupId())
        || StringUtil.isEmpty(id.getArtifactId())
        || StringUtil.isEmpty(id.getVersion())) {
      return false;
    }
    if (isVersionRange(id)) return true; // todo handle ranges more sensibly
    return manager.hasVersion(id.getGroupId(), id.getArtifactId(), id.getVersion());
  }

  private boolean isVersionRange(MavenId id) {
    String version = id.getVersion().trim();
    return version.startsWith("(") || version.startsWith("[");
  }

  @Override
  protected Set<String> doGetVariants(MavenId id, MavenProjectIndicesManager manager
  ) {
    return manager.getVersions(id.getGroupId(), id.getArtifactId());
  }
}