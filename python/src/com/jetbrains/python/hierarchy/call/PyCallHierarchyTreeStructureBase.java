// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.hierarchy.call;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.jetbrains.python.hierarchy.PyHierarchyNodeDescriptor;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author novokrest
 */
public abstract class PyCallHierarchyTreeStructureBase extends HierarchyTreeStructure {
  private final String myScopeType;

  public PyCallHierarchyTreeStructureBase(Project project, PsiElement element, String currentScopeType) {
    super(project, new PyHierarchyNodeDescriptor(null, element, true));
    myScopeType = currentScopeType;
  }

  @NotNull
  protected abstract Map<PsiElement, Collection<PsiElement>> getChildren(@NotNull PyElement element);

  @Override
  protected Object @NotNull [] buildChildren(@NotNull HierarchyNodeDescriptor descriptor) {
    final List<PyHierarchyNodeDescriptor> descriptors = new ArrayList<>();
    if (descriptor instanceof PyHierarchyNodeDescriptor) {
      final PyHierarchyNodeDescriptor pyDescriptor = (PyHierarchyNodeDescriptor)descriptor;
      final PsiElement element = pyDescriptor.getPsiElement();
      final boolean isCallable = element instanceof PyFunction || element instanceof PyClass || element instanceof PyFile;
      HierarchyNodeDescriptor nodeDescriptor = getBaseDescriptor();
      if (!(element instanceof PyElement) || !isCallable || nodeDescriptor == null) {
        return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
      }

      final Map<PsiElement, Collection<PsiElement>> children = getChildren((PyElement)element);

      final HashMap<PsiElement, PyHierarchyNodeDescriptor> callerToDescriptorMap = new HashMap<>();
      PsiElement baseClass = element instanceof PyFunction ? ((PyFunction)element).getContainingClass() : null;

      children.forEach((caller, usages) -> {
        if (isInScope(baseClass, caller, myScopeType)) {
          PyHierarchyNodeDescriptor callerDescriptor = callerToDescriptorMap.get(caller);
          if (callerDescriptor == null) {
            callerDescriptor = new PyHierarchyNodeDescriptor(descriptor, caller, usages, false);
            callerToDescriptorMap.put(caller, callerDescriptor);
            descriptors.add(callerDescriptor);
          }
        }
      });
    }
    return ArrayUtil.toObjectArray(descriptors);
  }
}
