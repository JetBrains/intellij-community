// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.hierarchy;

import com.intellij.ide.hierarchy.HierarchyBrowserManager;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public final class PyHierarchyUtils {
  private static final Comparator<NodeDescriptor<?>> NODE_DESCRIPTOR_COMPARATOR = Comparator.comparingInt(NodeDescriptor::getIndex);

  private PyHierarchyUtils() {
  }

  public static @NotNull Comparator<NodeDescriptor<?>> getComparator(final Project project) {
    if (HierarchyBrowserManager.getInstance(project).getState().SORT_ALPHABETICALLY) {
      return AlphaComparator.getInstance();
    }
    else {
      return NODE_DESCRIPTOR_COMPARATOR;
    }
  }
}
