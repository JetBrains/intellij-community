package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xml.ConvertContext;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;
import org.jetbrains.idea.maven.project.MavenId;

import java.util.Set;

public class MavenArtifactCoordinatesVersionConverter extends MavenArtifactCoordinatesConverter {
  @Override
  protected boolean doIsValid(MavenId id, MavenProjectIndicesManager manager, ConvertContext context) {
    if (StringUtil.isEmpty(id.groupId)
        || StringUtil.isEmpty(id.artifactId)
        || StringUtil.isEmpty(id.version)) {
      return false;
    }
    if (isVersionRange(id)) return true; // todo handle ranges more sensibly
    return manager.hasVersion(id.groupId, id.artifactId, id.version);
  }

  private boolean isVersionRange(MavenId id) {
    String version = id.version.trim();
    return version.startsWith("(") || version.startsWith("[");
  }

  @Override
  protected Set<String> doGetVariants(MavenId id, MavenProjectIndicesManager manager
  ) {
    return manager.getVersions(id.groupId, id.artifactId);
  }
}