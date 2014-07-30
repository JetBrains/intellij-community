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
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.debugger.PyHierarchyCallCacheManager;
import com.jetbrains.python.debugger.PyHierarchyCalleeData;
import com.jetbrains.python.debugger.PyHierarchyCallerData;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;


public class PyCalleeFunctionTreeStructure extends HierarchyTreeStructure {
  private final String myScopeType;

  public PyCalleeFunctionTreeStructure(Project project, PyFunction function, String currentScopeType) {
    super(project, new PyCallHierarchyNodeDescriptor(project, null, function, true, false));
    myScopeType = currentScopeType;
  }

  @NotNull
  @Override
  protected Object[] buildChildren(@NotNull HierarchyNodeDescriptor descriptor) {
    final PyFunction function = ((PyCallHierarchyNodeDescriptor)descriptor).getEnclosingElement();
    HierarchyNodeDescriptor nodeDescriptor = getBaseDescriptor();
    if (function == null || nodeDescriptor == null) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    List<PyCallHierarchyNodeDescriptor> descriptors = new ArrayList<PyCallHierarchyNodeDescriptor>();

    PyHierarchyCallCacheManager callCacheManager = PyHierarchyCallCacheManager.getInstance(myProject);
    Object[] callees = callCacheManager.findFunctionCallees(function);
    if (callees.length > 0) {
      for (Object calleeData: callees) {
        PyHierarchyCalleeData data = (PyHierarchyCalleeData)calleeData;
        VirtualFile calleeFile = LocalFileSystem.getInstance().findFileByPath(data.getCalleeFile());
        if (calleeFile == null) {
          continue;
        }
        PsiFile file = PsiManager.getInstance(myProject).findFile(calleeFile);
        if (!(file instanceof PyFile)) {
          continue;
        }
        PyFile pyCalleeFile = (PyFile)file;
        PsiElement callee = pyCalleeFile.getElementNamed(data.getCalleeName());
        if (callee instanceof PyFunction) {
          descriptors.add(new PyCallHierarchyNodeDescriptor(myProject, null, callee, false, false));
        }
      }
    }

    return ArrayUtil.toObjectArray(descriptors);
  }
}
