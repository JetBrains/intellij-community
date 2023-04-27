// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.structure;

import com.intellij.icons.AllIcons;
import org.jetbrains.idea.maven.project.MavenProject;

import static org.jetbrains.idea.maven.project.MavenProjectBundle.message;

class DependenciesNode extends BaseDependenciesNode {
  DependenciesNode(MavenProjectsStructure structure, ProjectNode parent, MavenProject mavenProject) {
    super(structure, parent, mavenProject);
    getTemplatePresentation().setIcon(AllIcons.Nodes.PpLibFolder);
  }

  @Override
  public String getName() {
    return message("view.node.dependencies");
  }

  public void updateDependencies() {
    updateChildren(myMavenProject.getDependencyTree(), myMavenProject);
  }
}
