package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xml.ConvertContext;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.indices.MavenIndexException;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;

import java.util.Set;

public class MavenVersionConverter extends MavenArtifactConverter {
  @Override
  protected boolean isValid(Project project, MavenProjectIndicesManager manager, MavenId id, ConvertContext context) throws MavenIndexException {
    if (StringUtil.isEmpty(id.version)) return false;
    if (isVersionRange(id)) return true; // todo handle ranges more sensibly
    return manager.hasVersion(id.groupId, id.artifactId, id.version);
  }

  private boolean isVersionRange(MavenId id) {
    String version = id.version.trim();
    return version.startsWith("(") || version.startsWith("[");
  }

  @Override
  protected Set<String> getVariants(Project project, MavenProjectIndicesManager manager, MavenId id, ConvertContext context)
      throws MavenIndexException {
    return manager.getVersions(id.groupId, id.artifactId);
  }
}