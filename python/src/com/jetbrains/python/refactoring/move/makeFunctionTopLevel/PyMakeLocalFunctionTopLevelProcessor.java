// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.move.makeFunctionTopLevel;

import com.google.common.collect.Lists;
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

  public PyMakeLocalFunctionTopLevelProcessor(@NotNull PyFunction targetFunction, @NotNull String destination) {
    super(targetFunction, destination);
  }

  @Override
  protected @NotNull String getRefactoringName() {
    return PyBundle.message("refactoring.make.local.function.top.level.dialog.title");
  }

  @Override
  protected void updateUsages(@NotNull Collection<String> newParamNames, UsageInfo @NotNull [] usages) {
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
  protected @NotNull PyFunction createNewFunction(@NotNull Collection<String> newParamNames) {
    final PyFunction copied = (PyFunction)myFunction.copy();
    addParameters(copied.getParameterList(), newParamNames);
    return copied;
  }

  @Override
  protected @NotNull List<String> collectNewParameterNames() {
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
