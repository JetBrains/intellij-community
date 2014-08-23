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
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;


public class PyDynamicCallDataManagerImpl extends PyDynamicCallDataManager {
  private final static String MODULE_CALLABLE_NAME = "<module>";
  private final static String LAMBDA_CALLER_NAME = "<lambda>";

  private final Project myProject;

  public PyDynamicCallDataManagerImpl(Project project) {
    myProject = project;
  }

  @Override
  public List<PsiElement> getCallees(@NotNull PyFunction element) {
    List<PsiElement> callees = Lists.newArrayList();

    PyHierarchyCallCacheManager callCacheManager = PyHierarchyCallCacheManager.getInstance(myProject);
    Object[] dynamicCallees = callCacheManager.findCallees(element);
    if (dynamicCallees.length > 0) {
      for (Object calleeData: dynamicCallees) {
        PyHierarchyCallData data = (PyHierarchyCallData)calleeData;
        resolveElement(data.getCalleeFile(), data.getCalleeName(), callees);
      }
    }
    return callees;
  }

  @Override
  public List<PsiElement> getCallers(@NotNull PyFunction element) {
    List<PsiElement> callers = Lists.newArrayList();

    PyHierarchyCallCacheManager callCacheManager = PyHierarchyCallCacheManager.getInstance(myProject);
    Object[] dynamicCallers = callCacheManager.findCallers(element);
    if (dynamicCallers.length > 0) {
      for (Object callerData: dynamicCallers) {
        PyHierarchyCallData data = (PyHierarchyCallData)callerData;
        resolveElement(data.getCallerFile(), data.getCallerName(), callers);
      }
    }
    return callers;
  }

  private void resolveElement(final String fileName, final String elementName, final Collection<PsiElement> result) {
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(fileName);
    if (virtualFile == null) {
      return;
    }
    PsiFile file = PsiManager.getInstance(myProject).findFile(virtualFile);
    if (!(file instanceof PyFile)) {
      return;
    }
    PyFile pyFile = (PyFile)file;
    if (elementName.equals(MODULE_CALLABLE_NAME)) {
      result.add(pyFile);
    }

    final List<String> parts = Lists.newArrayList(elementName.split("\\."));
    PyRecursiveElementVisitor visitor = new PyRecursiveElementVisitor() {
      private void visitRequiredElement(PyElement element) {
        if (parts.size() == 0) {
          return;
        }
        if (element.getName().equals(parts.get(0))) {
          parts.remove(0);
          if (parts.size() == 0) {
            result.add(element);
            return;
          }
          super.visitPyElement(element);
        }
      }

      @Override
      public void visitPyFile(PyFile pyFile) {
          parts.remove(0);
          super.visitPyFile(pyFile);
      }

      @Override
      public void visitPyClass(PyClass pyClass) {
        visitRequiredElement(pyClass);
      }

      @Override
      public void visitPyFunction(PyFunction pyFunction) {
        visitRequiredElement(pyFunction);
      }
    };
    visitor.visitPyFile(pyFile);
  }
}
