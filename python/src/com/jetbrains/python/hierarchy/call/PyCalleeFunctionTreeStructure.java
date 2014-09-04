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

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.hash.HashMap;
import com.jetbrains.python.hierarchy.PyHierarchyNodeDescriptor;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author novokrest
 */
public class PyCalleeFunctionTreeStructure extends HierarchyTreeStructure {
  private final String myScopeType;

  public PyCalleeFunctionTreeStructure(Project project, PsiElement element, String currentScopeType) {
    super(project, new PyHierarchyNodeDescriptor(null, element, true));
    myScopeType = currentScopeType;
  }

  @NotNull
  @Override
  protected Object[] buildChildren(@NotNull HierarchyNodeDescriptor descriptor) {
    final List<PyHierarchyNodeDescriptor> descriptors = new ArrayList<PyHierarchyNodeDescriptor>();
    if (descriptor instanceof PyHierarchyNodeDescriptor) {
      final PyHierarchyNodeDescriptor pyDescriptor = (PyHierarchyNodeDescriptor)descriptor;
      final PsiElement element = pyDescriptor.getPsiElement();
      final boolean isCallable = element instanceof PyFunction || element instanceof PyClass || element instanceof PyFile;
      HierarchyNodeDescriptor nodeDescriptor = getBaseDescriptor();
      if (!(element instanceof PyElement) || !isCallable || nodeDescriptor == null) {
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
      }

      final List<PsiElement> callees = new ArrayList<PsiElement>();

      PyCallDataManager[] functionManagers = {
        // TODO: Add dynamic call data manager
        PyStaticCallDataManager.getInstance(myProject),
      };

      for (PyCallDataManager functionManager : functionManagers) {
        callees.addAll(functionManager.getCallees((PyElement)element));
      }

      final Map<PsiElement, PyHierarchyNodeDescriptor> calleeToDescriptorMap = new HashMap<PsiElement, PyHierarchyNodeDescriptor>();
      PsiElement baseClass = element instanceof PyFunction ? ((PyFunction)element).getContainingClass() : null;

      for (PsiElement callee : callees) {
        if (isInScope(baseClass, callee, myScopeType)) {
          PyHierarchyNodeDescriptor calleeDescriptor = calleeToDescriptorMap.get(callee);
          if (calleeDescriptor == null) {
            calleeDescriptor = new PyHierarchyNodeDescriptor(descriptor, callee, false);
            calleeToDescriptorMap.put(callee, calleeDescriptor);
            descriptors.add(calleeDescriptor);
          }
        }
      }

    }
    return ArrayUtil.toObjectArray(descriptors);
  }

  @Override
  public boolean isAlwaysShowPlus() {
    return true;
  }
}
