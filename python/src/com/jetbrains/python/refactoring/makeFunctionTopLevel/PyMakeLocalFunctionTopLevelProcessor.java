/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.refactoring.makeFunctionTopLevel;

import com.google.common.collect.Lists;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
public class PyMakeLocalFunctionTopLevelProcessor extends PyBaseMakeFunctionTopLevelProcessor {

  protected PyMakeLocalFunctionTopLevelProcessor(@NotNull PyFunction targetFunction, @NotNull Editor editor) {
    super(targetFunction, editor);
    setPreviewUsages(false);
  }

  @Override
  @NotNull
  protected String getRefactoringName() {
    return PyBundle.message("refactoring.make.local.function.top.level");
  }

  @Override
  protected void updateUsages(@NotNull Collection<String> newParamNames, @NotNull UsageInfo[] usages) {
    for (UsageInfo usage : usages) {
      final PsiElement element = usage.getElement();
      if (element != null) {
        final PyCallExpression parentCall = as(element.getParent(), PyCallExpression.class);
        if (parentCall != null && parentCall.getArgumentList() != null) {
          addArguments(parentCall.getArgumentList(), newParamNames);
        }
      }
    }
  }

  @Override
  @NotNull
  protected PyFunction createNewFunction(@NotNull Collection<String> newParamNames) {
    final PyFunction copied = (PyFunction)myFunction.copy();
    addParameters(copied.getParameterList(), newParamNames);
    return copied;
  }

  @Override
  @NotNull
  protected List<String> collectNewParameterNames() {
    final Set<String> enclosingScopeReads = new LinkedHashSet<>();
    for (ScopeOwner owner : PsiTreeUtil.collectElementsOfType(myFunction, ScopeOwner.class)) {
      final AnalysisResult result = analyseScope(owner);
      if (!result.nonlocalWritesToEnclosingScope.isEmpty()) {
        throw new IncorrectOperationException(PyBundle.message("refactoring.make.function.top.level.error.nonlocal.writes"));
      }
      if (!result.readsOfSelfParametersFromEnclosingScope.isEmpty()) {
        throw new IncorrectOperationException(PyBundle.message("refactoring.make.function.top.level.error.self.reads"));
      }
      for (PsiElement element : result.readsFromEnclosingScope) {
        if (element instanceof PyElement) {
          ContainerUtil.addIfNotNull(enclosingScopeReads, ((PyElement)element).getName());
        }
      }
    }
    return Lists.newArrayList(enclosingScopeReads);
  }
}
