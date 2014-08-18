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
import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonProcessors;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;


public class PyStaticFunctionCallInfoManagerImpl extends PyStaticFunctionCallInfoManager {
  private final Project myProject;

  public PyStaticFunctionCallInfoManagerImpl(Project project) {
    myProject = project;
  }

  @Override
  public Collection<PyFunction> getCallees(@NotNull PyFunction function) {
    final List<PyFunction> callees = Lists.newArrayList();

    final PyRecursiveElementVisitor visitor = new PyRecursiveElementVisitor() {
      @Override
      public void visitPyFunction(PyFunction innerFunction) {
        return;
      }

      @Override
      public void visitPyCallExpression(PyCallExpression callExpression) {
        PsiElement calleeFunction = callExpression.resolveCalleeFunction(PyResolveContext.defaultContext());
        if (calleeFunction instanceof PyFunction) {
          callees.add((PyFunction)calleeFunction);
        }
      }
    };

    visitor.visitElement(function);

    return callees;
  }

  @Override
  public Collection<PyFunction> getCallers(@NotNull PyFunction function) {
    final Collection<PyFunction> callers = Lists.newArrayList();

    final Collection<UsageInfo> usages = findUsages(function);
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

      PsiElement caller = PsiTreeUtil.getParentOfType(element, PyFunction.class, false);
      if (caller != null) {
        callers.add((PyFunction)caller);
      }
    }

    return callers;
  }

  private Collection<UsageInfo> findUsages(@NotNull final PyFunction function) {
    final FindUsagesHandler handler =
      ((FindManagerImpl)FindManager.getInstance(myProject)).getFindUsagesManager().getFindUsagesHandler(function, true);

    final CommonProcessors.CollectProcessor<UsageInfo> processor = new CommonProcessors.CollectProcessor<UsageInfo>();
    final PsiElement[] psiElements = ArrayUtil.mergeArrays(handler.getPrimaryElements(), handler.getSecondaryElements());
    final FindUsagesOptions options = handler.getFindUsagesOptions(null);
    for (PsiElement psiElement : psiElements) {
      handler.processElementUsages(psiElement, processor, options);
    }
    return processor.getResults();
  }
}
