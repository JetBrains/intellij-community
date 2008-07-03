package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xml.ConvertContext;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.dom.model.Plugin;
import org.jetbrains.idea.maven.indices.MavenIndexException;
import org.jetbrains.idea.maven.indices.MavenIndicesManager;
import org.jetbrains.idea.maven.indices.MavenPluginInfoReader;

import java.util.HashSet;
import java.util.Set;

public class MavenVersionConverter extends MavenArtifactConverter {
  @Override
  protected boolean isValid(Project project, MavenIndicesManager manager, MavenId id, ConvertContext context) throws MavenIndexException {
    if (StringUtil.isEmpty(id.groupId)) {
      Plugin plugin = MavenArtifactConverterHelper.getMavenPlugin(context);
      if (plugin != null) {
        for (String each : MavenPluginInfoReader.DEFAULT_GROUPS) {
          if (manager.hasVersion(project, each, id.artifactId, id.version)) return true;
        }
      }
      return false;
    }

    return manager.hasVersion(project, id.groupId, id.artifactId, id.version);
  }

  @Override
  protected Set<String> getVariants(Project project, MavenIndicesManager manager, MavenId id, ConvertContext context)
      throws MavenIndexException {
    if (StringUtil.isEmpty(id.groupId)) {
      Set<String> result = new HashSet<String>();
      Plugin plugin = MavenArtifactConverterHelper.getMavenPlugin(context);
      if (plugin != null) {
        for (String each : MavenPluginInfoReader.DEFAULT_GROUPS) {
          result.addAll(manager.getVersions(project, each, id.artifactId));
        }
      }
      return result;
    }

    return manager.getVersions(project, id.groupId, id.artifactId);
  }
}