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
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;


public class PyStaticFunctionCallInfoManagerImpl extends PyStaticFunctionCallInfoManager {

  public PyStaticFunctionCallInfoManagerImpl(Project project) {
  }

  @Override
  public List<PyFunction> getCallees(@NotNull PyFunction function) {
    List<PyFunction> callees = Lists.newArrayList();
    findCallees(function, callees);

    return callees;
  }

  @Override
  public List<PyFunction> getCallers(@NotNull PyFunction function) {
    List<PyFunction> callers = Lists.newArrayList();

    final List<UsageInfo> usages = Lists.newArrayList();
    usages.addAll(PyRefactoringUtil.findUsages(function, false));
    for (UsageInfo usage: usages) {
      PsiElement element = usage.getElement();
      if (element != null) {
        element = element.getParent();
      }

      if (element instanceof PyArgumentList) {
        PyCallExpression callExpression = (PyCallExpression)element.getParent();
        PsiElement caller = callExpression.resolveCalleeFunction(PyResolveContext.defaultContext());
        if (caller instanceof PyFunction) {
          callers.add((PyFunction)caller);
        }
      }

      if (element instanceof PyCallExpression) {
        PsiElement caller = PsiTreeUtil.getParentOfType(element, PyFunction.class);
        if (caller != null) {
          callers.add((PyFunction)caller);
        }
      }
    }

    return callers;
  }

  private static void findCallees(final PsiElement element, List<PyFunction> callees) {
    final PsiElement[] children = element.getChildren();
    for (PsiElement child: children) {
      findCallees(child, callees);
      if (child instanceof PyCallExpression) {
        PyCallExpression callExpression = (PyCallExpression)child;
        PsiElement function = callExpression.resolveCalleeFunction(PyResolveContext.defaultContext());
        if (function instanceof PyFunction) {
          callees.add((PyFunction)function);
        }
      }
    }
  }
}
