// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.structure;

import com.intellij.icons.AllIcons;
import org.jetbrains.idea.maven.model.MavenConstants;

import static org.jetbrains.idea.maven.project.MavenProjectBundle.message;

class LifecycleNode extends GoalsGroupNode {
  LifecycleNode(MavenProjectsStructure structure, ProjectNode parent) {
    super(structure, parent);

    for (String goal : MavenConstants.PHASES) {
      myGoalNodes.add(new StandardGoalNode(structure, this, goal));
    }
    getTemplatePresentation().setIcon(AllIcons.Nodes.ConfigFolder);
  }

  @Override
  public String getName() {
    return message("view.node.lifecycle");
  }

  public void updateGoalsList() {
    childrenChanged();
  }
}
