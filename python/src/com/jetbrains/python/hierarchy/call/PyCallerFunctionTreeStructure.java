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
import com.intellij.util.containers.HashMap;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author novokrest
 */
public class PyCallerFunctionTreeStructure extends HierarchyTreeStructure {
  private final String myScopeType;

  public PyCallerFunctionTreeStructure(Project project, PsiElement element, String currentScopeType) {
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

    final List<PsiElement> callers = Lists.newArrayList();

    PyCallDataManager[] functionManagers = {
      // TODO: Add dynamic call data manager
      PyStaticCallDataManager.getInstance(myProject),
    };

    for (PyCallDataManager functionManager : functionManagers) {
      callers.addAll(functionManager.getCallers(element));
    }

    final HashMap<PsiElement, PyCallHierarchyNodeDescriptor> callerToDescriptorMap = new HashMap<PsiElement, PyCallHierarchyNodeDescriptor>();
    final List<PyCallHierarchyNodeDescriptor> descriptors = Lists.newArrayList();
    PsiElement baseClass = element instanceof PyFunction ? ((PyFunction)element).getContainingClass() : null;

    for (PsiElement caller : callers) {
      if (baseClass != null && !isInScope(baseClass, caller, myScopeType)) continue;

      PyCallHierarchyNodeDescriptor callerDescriptor = callerToDescriptorMap.get(caller);
      if (callerDescriptor == null) {
        callerDescriptor = new PyCallHierarchyNodeDescriptor(myProject, null, caller, false, false);
        callerToDescriptorMap.put(caller, callerDescriptor);
        descriptors.add(callerDescriptor);
      }
    }

    return ArrayUtil.toObjectArray(descriptors);
  }

  @Override
  public boolean isAlwaysShowPlus() {
    return true;
  }
}
