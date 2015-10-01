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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
public class PyMakeMethodTopLevelProcessor extends PyBaseMakeFunctionTopLevelProcessor {

  public PyMakeMethodTopLevelProcessor(@NotNull PyFunction targetFunction, @NotNull Editor editor) {
    super(targetFunction, editor);
    // It's easier to debug without preview
    setPreviewUsages(!ApplicationManager.getApplication().isInternal());
  }

  @NotNull
  @Override
  protected String getRefactoringName() {
    return PyBundle.message("refactoring.make.method.top.level");
  }

  @Override
  protected void updateExistingFunctionUsages(@NotNull Collection<String> newParamNames, @NotNull UsageInfo[] usages) {

  }

  @NotNull
  @Override
  protected PyFunction createNewFunction(@NotNull Collection<String> newParamNames) {
    final PyFunction copied = (PyFunction)myFunction.copy();
    final PyParameter[] params = copied.getParameterList().getParameters();
    if (params.length > 0) {
      params[0].delete();
    }
    return addParameters(copied, newParamNames);
  }

  @NotNull
  @Override
  protected Set<String> collectNewParameterNames() {
    final Set<String> paramNames = new LinkedHashSet<String>();
    for (ScopeOwner owner : PsiTreeUtil.collectElementsOfType(myFunction, ScopeOwner.class)) {
      final AnalysisResult result = analyseScope(owner);
      if (!result.nonlocalWritesToEnclosingScope.isEmpty()) {
        throw new IncorrectOperationException(PyBundle.message("refactoring.make.function.top.level.error.nonlocal.writes"));
      }
      if (!result.readsOfSelfParametersFromEnclosingScope.isEmpty()) {
        throw new IncorrectOperationException(PyBundle.message("refactoring.make.function.top.level.error.self.reads"));
      }
      if (!result.readsFromEnclosingScope.isEmpty()) {
        throw new IncorrectOperationException(PyBundle.message("refactoring.make.function.top.level.error.outer.scope.reads"));
      }
      if (!result.writesToSelfParameter.isEmpty()) {
        throw new IncorrectOperationException(PyBundle.message("refactoring.make.function.top.level.error.special.usage.of.self"));
      }
      for (PsiElement usage : result.readsOfSelfParameter) {
        if (usage.getParent() instanceof PyTargetExpression) {
          throw new IncorrectOperationException(PyBundle.message("refactoring.make.function.top.level.error.attribute.writes"));
        }
        final PyReferenceExpression parentReference = as(usage.getParent(), PyReferenceExpression.class);
        if (parentReference != null) {
          final String attrName = parentReference.getName();
          if (attrName != null && PyUtil.isClassPrivateName(attrName)) {
            throw new IncorrectOperationException(PyBundle.message("refactoring.make.function.top.level.error.private.attributes"));
          }
          if (parentReference.getParent() instanceof PyCallExpression) {
            throw new IncorrectOperationException(PyBundle.message("refactoring.make.function.top.level.error.method.calls"));
          }
          ContainerUtil.addIfNotNull(paramNames, attrName);
        }
        else {
          throw new IncorrectOperationException(PyBundle.message("refactoring.make.function.top.level.error.special.usage.of.self"));
        }
      }
    }
    return paramNames;
  }
}
