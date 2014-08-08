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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.python.debugger.PyHierarchyCallCacheManager;
import com.jetbrains.python.debugger.PyHierarchyCalleeData;
import com.jetbrains.python.debugger.PyHierarchyCallerData;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

import java.util.List;


public class PyDynamicFunctionCallInfoManagerImpl extends PyDynamicFunctionCallInfoManager {
  private final Project myProject;

  public PyDynamicFunctionCallInfoManagerImpl(Project project) {
    myProject = project;
  }

  @Override
  public List<PyFunction> getCallees(@NotNull PyFunction function) {
    List<PyFunction> callees = Lists.newArrayList();

    PyHierarchyCallCacheManager callCacheManager = PyHierarchyCallCacheManager.getInstance(myProject);
    Object[] dynamicCallees = callCacheManager.findFunctionCallees(function);
    if (dynamicCallees.length > 0) {
      for (Object calleeData: dynamicCallees) {
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
          callees.add((PyFunction)callee);
        }
      }
    }
    return callees;
  }

  @Override
  public List<PyFunction> getCallers(@NotNull PyFunction function) {
    List<PyFunction> callers = Lists.newArrayList();

    PyHierarchyCallCacheManager callCacheManager = PyHierarchyCallCacheManager.getInstance(myProject);
    Object[] dynamicCallers = callCacheManager.findFunctionCallers(function);
    if (dynamicCallers.length > 0) {
      for (Object callerData: dynamicCallers) {
        PyHierarchyCallerData data = (PyHierarchyCallerData)callerData;
        VirtualFile callerFile = LocalFileSystem.getInstance().findFileByPath(data.getCallerFile());
        if (callerFile == null) {
          continue;
        }
        PsiFile file = PsiManager.getInstance(myProject).findFile(callerFile);
        if (!(file instanceof PyFile)) {
          continue;
        }
        PyFile pyCallerFile = (PyFile)file;
        PsiElement caller = pyCallerFile.getElementNamed(data.getCallerName());
        if (caller instanceof PyFunction) {
          callers.add((PyFunction)caller);
        }
      }
    }
    return callers;
  }
}
