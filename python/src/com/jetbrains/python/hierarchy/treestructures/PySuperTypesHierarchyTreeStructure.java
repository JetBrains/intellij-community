// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  public PySuperTypesHierarchyTreeStructure(@NotNull final PyClass cl) {
    super(cl.getProject(), new PyHierarchyNodeDescriptor(null, cl, true));
  }

  @Override
  @NotNull
  protected Object[] buildChildren(@NotNull HierarchyNodeDescriptor descriptor) {
    final List<PyHierarchyNodeDescriptor> res = new ArrayList<>();
    if (descriptor instanceof PyHierarchyNodeDescriptor) {
      final PyHierarchyNodeDescriptor pyDescriptor = (PyHierarchyNodeDescriptor)descriptor;
      final PsiElement element = pyDescriptor.getPsiElement();
      if (element instanceof PyClass) {
        final PyClass cls = (PyClass)element;
        final TypeEvalContext context = TypeEvalContext.codeAnalysis(cls.getProject(), cls.getContainingFile());
        for (PyClass superClass : cls.getSuperClasses(context)) {
          res.add(new PyHierarchyNodeDescriptor(descriptor, superClass, false));
        }
      }
    }
    return res.toArray();
  }
}
