// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  public PySubTypesHierarchyTreeStructure(@NotNull final PyClass cl) {
    super(cl.getProject(), new PyHierarchyNodeDescriptor(null, cl, true));
  }

  @Override
  @NotNull
  protected Object[] buildChildren(@NotNull HierarchyNodeDescriptor descriptor) {
    final List<PyHierarchyNodeDescriptor> res = new ArrayList<>();
    final PsiElement element = ((PyHierarchyNodeDescriptor)descriptor).getPsiElement();
    if (element instanceof PyClass) {
      final PyClass cls = (PyClass)element;
      Query<PyClass> subClasses = PyClassInheritorsSearch.search(cls, false);
      for (PyClass subClass : subClasses) {
        res.add(new PyHierarchyNodeDescriptor(descriptor, subClass, false));
      }

    }

    return ArrayUtil.toObjectArray(res);
  }
}
