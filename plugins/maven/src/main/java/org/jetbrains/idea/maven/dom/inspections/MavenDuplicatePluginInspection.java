// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import com.intellij.util.xml.highlighting.DomElementsInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.model.MavenDomPlugin;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.project.MavenProjectBundle;

import java.util.Collection;
import java.util.Map;

public final class MavenDuplicatePluginInspection extends DomElementsInspection<MavenDomProjectModel> {
  public MavenDuplicatePluginInspection() {
    super(MavenDomProjectModel.class);
  }

  @Override
  public void checkFileElement(@NotNull DomFileElement<MavenDomProjectModel> domFileElement,
                               @NotNull DomElementAnnotationHolder holder) {
    MavenDomProjectModel projectModel = domFileElement.getRootElement();

    MultiMap<Pair<String,String>, MavenDomPlugin> duplicates = MultiMap.createSet();

    for (MavenDomPlugin plugin : projectModel.getBuild().getPlugins().getPlugins()) {
      String groupId = plugin.getGroupId().getStringValue();
      String artifactId = plugin.getArtifactId().getStringValue();

      if (StringUtil.isEmptyOrSpaces(artifactId)) continue;

      if ("".equals(groupId) || "org.apache.maven.plugins".equals(groupId) || "org.codehaus.mojo".equals(groupId)) {
        groupId = null;
      }

      duplicates.putValue(Pair.create(groupId, artifactId), plugin);
    }

    for (Map.Entry<Pair<String,String>, Collection<MavenDomPlugin>> entry : duplicates.entrySet()) {
      Collection<MavenDomPlugin> set = entry.getValue();
      if (set.size() <= 1) continue;

      for (MavenDomPlugin dependency : set) {
        holder.createProblem(dependency, HighlightSeverity.WARNING,
                             MavenProjectBundle.message("inspection.message.duplicated.plugin.declaration"));
      }
    }
  }

  @Override
  public @NotNull String getGroupDisplayName() {
    return MavenDomBundle.message("inspection.group");
  }

  @Override
  public @NotNull String getShortName() {
    return "MavenDuplicatePluginInspection";
  }

  @Override
  public @NotNull HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }
}