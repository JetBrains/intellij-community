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
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.debugger.PyHierarchyCallCacheManager;
import com.jetbrains.python.debugger.PyHierarchyCallerData;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;


public class PyCallerFunctionTreeStructure extends HierarchyTreeStructure {
  private final String myScopeType;

  public PyCallerFunctionTreeStructure(Project project, PyFunction function, String currentScopeType) {
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

    final SearchScope searchScope = getSearchScope(myScopeType, function.getContainingClass());
    final Set<PyFunction> functionsToFind = new HashSet<PyFunction>();
    final Collection<PsiElement> superMethods = PySuperMethodsSearch.search(function, true).findAll();
    functionsToFind.add(function);
    for (PsiElement superMethod : superMethods) {
      if (superMethod instanceof PyFunction) {
        functionsToFind.add((PyFunction)superMethod);
      }
    }

    final List<PyFunction> callers = Lists.newArrayList();
    final HashMap<PyFunction, PyCallHierarchyNodeDescriptor> callerToDescriptorMap = new HashMap<PyFunction, PyCallHierarchyNodeDescriptor>();
    final List<PyCallHierarchyNodeDescriptor> descriptors = new ArrayList<PyCallHierarchyNodeDescriptor>();

    final List<UsageInfo> usages = Lists.newArrayList();
    usages.addAll(PyRefactoringUtil.findUsages(function, false));
    final List<PsiElement> references = Lists.newArrayList();
    for (UsageInfo usage: usages) {
      PsiElement element = usage.getElement().getParent();

      if (element instanceof PyArgumentList) {
        PyCallExpression callExpression = (PyCallExpression)element.getParent();
        PsiElement def = callExpression.resolveCalleeFunction(PyResolveContext.defaultContext());
        if (def instanceof PyFunction) {
          descriptors.add(new PyCallHierarchyNodeDescriptor(myProject, null, def, false, false));
        }
      }

      if (element instanceof PyCallExpression) {
        PsiElement caller = PsiTreeUtil.getParentOfType(element, PyFunction.class);
        if (caller instanceof PyFunction) {
          callers.add((PyFunction)caller);
        }
      }

      if (element != null) {
        references.add(element);
      }
    }

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

    for (PyFunction caller: callers) {
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
