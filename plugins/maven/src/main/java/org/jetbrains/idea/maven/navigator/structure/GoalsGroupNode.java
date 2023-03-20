// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.structure;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

abstract class GoalsGroupNode extends GroupNode {
  protected final List<MavenGoalNode> myGoalNodes = new CopyOnWriteArrayList<>();

  GoalsGroupNode(MavenProjectsStructure structure, MavenSimpleNode parent) {
    super(structure, parent);
  }

  @Override
  protected List<? extends MavenSimpleNode> doGetChildren() {
    return myGoalNodes;
  }
}
