// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.hierarchy.treestructures;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Query;
import com.jetbrains.python.hierarchy.PyHierarchyNodeDescriptor;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.search.PyClassInheritorsSearch;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class PySubTypesHierarchyTreeStructure extends HierarchyTreeStructure {
  protected PySubTypesHierarchyTreeStructure(final Project project, final HierarchyNodeDescriptor baseDescriptor) {
    super(project, baseDescriptor);
  }

  public PySubTypesHierarchyTreeStructure(final @NotNull PyClass cl) {
    super(cl.getProject(), new PyHierarchyNodeDescriptor(null, cl, true));
  }

  @Override
  protected Object @NotNull [] buildChildren(@NotNull HierarchyNodeDescriptor descriptor) {
    final List<PyHierarchyNodeDescriptor> res = new ArrayList<>();
    final PsiElement element = descriptor.getPsiElement();
    if (element instanceof PyClass cls) {
      Query<PyClass> subClasses = PyClassInheritorsSearch.search(cls, false);
      for (PyClass subClass : subClasses.asIterable()) {
        res.add(new PyHierarchyNodeDescriptor(descriptor, subClass, false));
      }

    }

    return ArrayUtil.toObjectArray(res);
  }
}
