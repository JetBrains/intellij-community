/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.hierarchy.call;

import com.google.common.collect.Lists;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class PyCalleeFunctionTreeStructure extends HierarchyTreeStructure {
  private final String myScopeType;

  public PyCalleeFunctionTreeStructure(Project project, PsiElement element, String currentScopeType) {
    super(project, new PyCallHierarchyNodeDescriptor(project, null, element, true, false));
    myScopeType = currentScopeType;
  }

  @NotNull
  @Override
  protected Object[] buildChildren(@NotNull HierarchyNodeDescriptor descriptor) {
    final PyElement element = ((PyCallHierarchyNodeDescriptor)descriptor).getEnclosingElement();
    final boolean isCallable = element instanceof PyFunction || element instanceof PyClass || element instanceof PyFile;
    HierarchyNodeDescriptor nodeDescriptor = getBaseDescriptor();
    if (!isCallable || nodeDescriptor == null) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    final List<PsiElement> callees = Lists.newArrayList();
    PyCallDataManager[] functionManagers = {
      PyStaticCallDataManager.getInstance(myProject),
      PyDynamicCallDataManager.getInstance(myProject)
    };
    for (PyCallDataManager functionManager: functionManagers) {
      callees.addAll(functionManager.getCallees(element));
    }

    final Map<PsiElement, PyCallHierarchyNodeDescriptor> calleeToDescriptorMap = new HashMap<PsiElement, PyCallHierarchyNodeDescriptor>();
    final List<PyCallHierarchyNodeDescriptor> descriptors = Lists.newArrayList();
    PsiElement baseClass = element instanceof PyFunction ? ((PyFunction)element).getContainingClass() : null;

    for (PsiElement callee: callees) {
      if (baseClass != null && !isInScope(baseClass, callee, myScopeType)) continue;

      PyCallHierarchyNodeDescriptor calleeDescriptor = calleeToDescriptorMap.get(callee);
      if (calleeDescriptor == null) {
        calleeDescriptor = new PyCallHierarchyNodeDescriptor(myProject, null, callee, false, false);
        calleeToDescriptorMap.put(callee, calleeDescriptor);
        descriptors.add(calleeDescriptor);
      }
    }

    return ArrayUtil.toObjectArray(descriptors);
  }

  @Override
  public boolean isAlwaysShowPlus() {
    return true;
  }
}
