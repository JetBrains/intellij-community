// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.structure;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.project.MavenProject;

import static org.jetbrains.idea.maven.project.MavenProjectBundle.message;

public class DependenciesNode extends BaseDependenciesNode {
  public DependenciesNode(MavenProjectsStructure.ProjectNode parent, Project project, MavenProject mavenProject, MavenProjectsStructure.Customization customization) {
    super(parent, project, mavenProject, customization);
    getTemplatePresentation().setIcon(AllIcons.Nodes.PpLibFolder);
  }

  @Override
  public String getName() {
    return message("view.node.dependencies");
  }

  public void updateDependencies(MavenProjectsStructure mavenProjectsStructure) {
    updateChildren(myMavenProject.getDependencyTree(), myMavenProject, mavenProjectsStructure);
  }
}
