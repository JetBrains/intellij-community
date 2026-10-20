// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.hierarchy.treestructures;

import com.google.common.collect.Lists;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.jetbrains.python.hierarchy.PyHierarchyNodeDescriptor;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

public class PyTypeHierarchyTreeStructure extends PySubTypesHierarchyTreeStructure {
  public PyTypeHierarchyTreeStructure(final @NotNull PyClass cl) {
    super(cl.getProject(), buildHierarchyElement(cl));
    setBaseElement(myBaseDescriptor);
  }

  private static PyHierarchyNodeDescriptor buildHierarchyElement(final @NotNull PyClass cl) {
    PyHierarchyNodeDescriptor descriptor = null;
    final TypeEvalContext context = TypeEvalContext.codeAnalysis(cl.getProject(), cl.getContainingFile());
    for (PyClass superClass : Lists.reverse(cl.getAncestorClasses(context))) {
      final PyHierarchyNodeDescriptor newDescriptor = new PyHierarchyNodeDescriptor(descriptor, superClass, false);
      if (descriptor != null) {
        descriptor.setCachedChildren(new PyHierarchyNodeDescriptor[]{newDescriptor});
      }
      descriptor = newDescriptor;
    }
    final PyHierarchyNodeDescriptor newDescriptor = new PyHierarchyNodeDescriptor(descriptor, cl, true);
    if (descriptor != null) {
      descriptor.setCachedChildren(new HierarchyNodeDescriptor[]{newDescriptor});
    }
    return newDescriptor;
  }
}
