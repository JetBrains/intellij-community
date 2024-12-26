// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.structure;

import com.intellij.pom.Navigatable;
import com.intellij.pom.NavigatableAdapter;
import com.intellij.psi.xml.XmlElement;
import icons.MavenIcons;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenPluginDomUtil;
import org.jetbrains.idea.maven.dom.plugin.MavenDomMojo;
import org.jetbrains.idea.maven.dom.plugin.MavenDomPluginModel;

import java.util.Objects;

class PluginGoalNode extends MavenGoalNode {

  private final String myUnqualifiedGoal;

  PluginGoalNode(MavenProjectsStructure structure, PluginNode parent, String goal, String unqualifiedGoal, String displayName) {
    super(structure, parent, goal, displayName);
    getTemplatePresentation().setIcon(MavenIcons.PluginGoal);
    myUnqualifiedGoal = unqualifiedGoal;
  }

  @Override
  public @Nullable Navigatable getNavigatable() {
    PluginNode pluginNode = (PluginNode)getParent();

    MavenDomPluginModel pluginModel = MavenPluginDomUtil.getMavenPluginModel(myProject,
                                                                             pluginNode.getPlugin().getGroupId(),
                                                                             pluginNode.getPlugin().getArtifactId(),
                                                                             pluginNode.getPlugin().getVersion());

    if (pluginModel == null) return null;

    for (MavenDomMojo mojo : pluginModel.getMojos().getMojos()) {
      final XmlElement xmlElement = mojo.getGoal().getXmlElement();

      if (xmlElement instanceof Navigatable && Objects.equals(myUnqualifiedGoal, mojo.getGoal().getStringValue())) {
        return new NavigatableAdapter() {
          @Override
          public void navigate(boolean requestFocus) {
            ((Navigatable)xmlElement).navigate(requestFocus);
          }
        };
      }
    }

    return null;
  }
}
