// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.structure;

import org.jetbrains.idea.maven.model.MavenConstants;

class StandardGoalNode extends MavenGoalNode {
  StandardGoalNode(MavenProjectsStructure structure, GoalsGroupNode parent, String goal) {
    super(structure, parent, goal, goal);
  }

  @Override
  public boolean isVisible() {
    if (myMavenProjectsStructure.showOnlyBasicPhases() && !MavenConstants.BASIC_PHASES.contains(getGoal())) return false;
    return super.isVisible();
  }
}
