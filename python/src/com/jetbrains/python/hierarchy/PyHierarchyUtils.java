// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.hierarchy;

import com.intellij.ide.hierarchy.HierarchyBrowserManager;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public final class PyHierarchyUtils {
  private static final Comparator<NodeDescriptor<?>> NODE_DESCRIPTOR_COMPARATOR = (first, second) -> first.getIndex() - second.getIndex();

  private PyHierarchyUtils() {
  }

  @NotNull
  public static Comparator<NodeDescriptor<?>> getComparator(final Project project) {
    if (HierarchyBrowserManager.getInstance(project).getState().SORT_ALPHABETICALLY) {
      return AlphaComparator.INSTANCE;
    }
    else {
      return NODE_DESCRIPTOR_COMPARATOR;
    }
  }
}
