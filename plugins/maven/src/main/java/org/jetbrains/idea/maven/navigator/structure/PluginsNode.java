// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.structure;

import com.intellij.icons.AllIcons;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.project.MavenProject;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.jetbrains.idea.maven.project.MavenProjectBundle.message;

class PluginsNode extends GroupNode {
  private final List<PluginNode> myPluginNodes = new CopyOnWriteArrayList<>();

  PluginsNode(MavenProjectsStructure structure, ProjectNode parent) {
    super(structure, parent);
    getTemplatePresentation().setIcon(AllIcons.Nodes.ConfigFolder);
  }

  @Override
  public String getName() {
    return message("view.node.plugins");
  }

  @Override
  protected List<? extends MavenSimpleNode> doGetChildren() {
    return myPluginNodes;
  }

  public void updatePlugins(MavenProject mavenProject) {

    List<MavenPlugin> plugins = mavenProject.getDeclaredPlugins();
    myMavenProjectsStructure.updatePluginsTree(this, plugins);
  }

  public List<PluginNode> getPluginNodes() {
    return myPluginNodes;
  }
}
