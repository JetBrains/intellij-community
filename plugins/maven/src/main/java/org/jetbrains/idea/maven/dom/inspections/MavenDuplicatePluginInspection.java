/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

import java.util.Collection;
import java.util.Map;

public class MavenDuplicatePluginInspection extends DomElementsInspection<MavenDomProjectModel> {
  public MavenDuplicatePluginInspection() {
    super(MavenDomProjectModel.class);
  }

  @Override
  public void checkFileElement(DomFileElement<MavenDomProjectModel> domFileElement,
                               DomElementAnnotationHolder holder) {
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
        holder.createProblem(dependency, HighlightSeverity.WARNING, "Duplicated plugin declaration");
      }
    }
  }

  @NotNull
  public String getGroupDisplayName() {
    return MavenDomBundle.message("inspection.group");
  }

  @NotNull
  public String getDisplayName() {
    return MavenDomBundle.message("inspection.duplicate.plugin.declaration");
  }

  @NotNull
  public String getShortName() {
    return "MavenDuplicatePluginInspection";
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }
}