// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.structure;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@ApiStatus.Internal
public abstract class GroupNode extends MavenSimpleNode {
  private static final Comparator<MavenSimpleNode> NODE_COMPARATOR = (o1, o2) -> StringUtil.compare(o1.getName(), o2.getName(), true);

  GroupNode(MavenProjectsStructure structure, MavenSimpleNode parent) {
    super(structure, parent);
  }

  @Override
  public boolean isVisible() {
    if (getDisplayKind() == MavenProjectsStructure.DisplayKind.ALWAYS) return true;

    for (SimpleNode each : getChildren()) {
      if (((MavenSimpleNode)each).isVisible()) return true;
    }
    return false;
  }

  protected <T extends MavenSimpleNode> void insertSorted(List<T> list, T newObject) {
    int pos = Collections.binarySearch(list, newObject, NODE_COMPARATOR);
    list.add(pos >= 0 ? pos : -pos - 1, newObject);
  }

  protected void sort(List<? extends MavenSimpleNode> list) {
    list.sort(NODE_COMPARATOR);
  }
}
