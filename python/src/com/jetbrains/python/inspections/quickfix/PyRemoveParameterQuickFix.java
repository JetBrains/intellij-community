/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.documentation.docstrings.PyDocstringGenerator;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableParameterImpl;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiUtil;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

public class PyRemoveParameterQuickFix implements LocalQuickFix {

  @Override
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("QFIX.NAME.remove.parameter");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PyParameter psi = PyUtil.as(descriptor.getPsiElement(), PyParameter.class);
    assert psi != null;
    final PyCallableParameter parameter = PyCallableParameterImpl.psi(psi);
    final TypeEvalContext ctx = TypeEvalContext.codeAnalysis(project, psi.getContainingFile());

    final PyFunction function = PsiTreeUtil.getParentOfType(psi, PyFunction.class);
    if (function != null) {
      final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(ctx);

      StreamEx
        .of(PyRefactoringUtil.findUsages(function, false))
        .map(UsageInfo::getElement)
        .nonNull()
        .map(PsiElement::getParent)
        .select(PyCallExpression.class)
        .flatCollection(callExpression -> callExpression.multiMapArguments(resolveContext))
        .flatCollection(mapping -> mapping.getMappedParameters().entrySet())
        .filter(entry -> parameter.equals(entry.getValue()))
        .forEach(entry -> entry.getKey().delete());

      final PyStringLiteralExpression docStringExpression = function.getDocStringExpression();
      final String parameterName = parameter.getName();
      if (docStringExpression != null && parameterName != null) {
        PyDocstringGenerator.forDocStringOwner(function).withoutParam(parameterName).buildAndInsert();
      }

      if (parameterName != null) {
        StreamEx
          .of(PyiUtil.getOverloads(function, ctx))
          .map(overload -> overload.getParameterList().getParameters())
          .map(parameters -> ContainerUtil.find(parameters, overloadParameter -> parameterName.equals(overloadParameter.getName())))
          .nonNull()
          .forEach(PsiElement::delete);
      }
    }

    psi.delete();
  }
}
