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
import com.jetbrains.python.debugger.PyHierarchyCallData;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

import java.util.List;


public class PyDynamicCallDataManagerImpl extends PyDynamicCallDataManager {
  private final static String MODULE_CALLER_NAME = "<module>";
  private final static String LAMBDA_CALLER_NAME = "<lambda>";

  private final Project myProject;

  public PyDynamicCallDataManagerImpl(Project project) {
    myProject = project;
  }

  @Override
  public List<PsiElement> getCallees(@NotNull PyFunction function) {
    List<PsiElement> callees = Lists.newArrayList();

    PyHierarchyCallCacheManager callCacheManager = PyHierarchyCallCacheManager.getInstance(myProject);
    Object[] dynamicCallees = callCacheManager.findCallees(function);
    if (dynamicCallees.length > 0) {
      for (Object calleeData: dynamicCallees) {
        PyHierarchyCallData data = (PyHierarchyCallData)calleeData;
        VirtualFile calleeFile = LocalFileSystem.getInstance().findFileByPath(data.getCalleeFile());
        if (calleeFile == null) {
          continue;
        }
        PsiFile file = PsiManager.getInstance(myProject).findFile(calleeFile);
        if (!(file instanceof PyFile)) {
          continue;
        }
        PyFile pyCalleeFile = (PyFile)file;
        String calleeName = data.getCalleeName();
        if (calleeName.equals(MODULE_CALLER_NAME)) {
          callees.add(pyCalleeFile);
        }
        PsiElement callee = pyCalleeFile.getElementNamed(calleeName);
        if (callee instanceof PyFunction || callee instanceof PyClass) {
          callees.add(callee);
        }
      }
    }
    return callees;
  }

  @Override
  public List<PsiElement> getCallers(@NotNull PyFunction function) {
    List<PsiElement> callers = Lists.newArrayList();

    PyHierarchyCallCacheManager callCacheManager = PyHierarchyCallCacheManager.getInstance(myProject);
    Object[] dynamicCallers = callCacheManager.findCallers(function);
    if (dynamicCallers.length > 0) {
      for (Object callerData: dynamicCallers) {
        PyHierarchyCallData data = (PyHierarchyCallData)callerData;
        VirtualFile callerFile = LocalFileSystem.getInstance().findFileByPath(data.getCallerFile());
        if (callerFile == null) {
          continue;
        }
        PsiFile file = PsiManager.getInstance(myProject).findFile(callerFile);
        if (!(file instanceof PyFile)) {
          continue;
        }
        PyFile pyCallerFile = (PyFile)file;
        String callerName = data.getCallerName();
        if (callerName.equals(MODULE_CALLER_NAME)) {
          callers.add(pyCallerFile);
          continue;
        }
        PsiElement caller = pyCallerFile.getElementNamed(callerName);
        if (caller instanceof PyFunction || caller instanceof PyClass) {
          callers.add(caller);
        }
      }
    }
    return callers;
  }
}
