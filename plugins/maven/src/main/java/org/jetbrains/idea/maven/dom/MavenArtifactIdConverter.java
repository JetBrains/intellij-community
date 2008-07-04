package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xml.ConvertContext;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.dom.model.Plugin;
import org.jetbrains.idea.maven.indices.MavenIndexException;
import org.jetbrains.idea.maven.indices.MavenPluginInfoReader;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;

import java.util.HashSet;
import java.util.Set;

public class MavenArtifactIdConverter extends MavenArtifactConverter {
  @Override
  protected boolean isValid(Project project, MavenProjectIndicesManager manager, MavenId id, ConvertContext context) throws MavenIndexException {
    if (StringUtil.isEmpty(id.groupId)) {
      Plugin plugin = MavenArtifactConverterHelper.getMavenPlugin(context);
      if (plugin != null) {
        for (String each : MavenPluginInfoReader.DEFAULT_GROUPS) {
          if (manager.hasArtifactId(each, id.artifactId)) return true;
        }
      }
      return false;
    }

    return manager.hasArtifactId(id.groupId, id.artifactId);
  }

  @Override
  protected Set<String> getVariants(Project project, MavenProjectIndicesManager manager, MavenId id, ConvertContext context) throws MavenIndexException {
    if (StringUtil.isEmpty(id.groupId)) {
      Set<String> result = new HashSet<String>();
      Plugin plugin = MavenArtifactConverterHelper.getMavenPlugin(context);
      if (plugin != null) {
        for (String each : MavenPluginInfoReader.DEFAULT_GROUPS) {
          result.addAll(manager.getArtifactIds(each));
        }
      }
      return result;
    }

    return manager.getArtifactIds(id.groupId);
  }
}
