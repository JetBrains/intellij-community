// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.hierarchy.treestructures;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.hierarchy.PyHierarchyNodeDescriptor;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class PySuperTypesHierarchyTreeStructure extends HierarchyTreeStructure {
  public PySuperTypesHierarchyTreeStructure(final @NotNull PyClass cl) {
    super(cl.getProject(), new PyHierarchyNodeDescriptor(null, cl, true));
  }

  @Override
  protected Object @NotNull [] buildChildren(@NotNull HierarchyNodeDescriptor descriptor) {
    final List<PyHierarchyNodeDescriptor> res = new ArrayList<>();
    if (descriptor instanceof PyHierarchyNodeDescriptor pyDescriptor) {
      final PsiElement element = pyDescriptor.getPsiElement();
      if (element instanceof PyClass cls) {
        final TypeEvalContext context = TypeEvalContext.codeAnalysis(cls.getProject(), cls.getContainingFile());
        for (PyClass superClass : cls.getSuperClasses(context)) {
          res.add(new PyHierarchyNodeDescriptor(descriptor, superClass, false));
        }
      }
    }
    return res.toArray();
  }
}
