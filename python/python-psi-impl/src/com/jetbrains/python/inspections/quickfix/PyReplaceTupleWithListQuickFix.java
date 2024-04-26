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
package com.jetbrains.python.inspections.quickfix;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

public class PyReplaceTupleWithListQuickFix extends PsiUpdateModCommandQuickFix {
  @NotNull
  @Override
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.NAME.make.list");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    assert element instanceof PyAssignmentStatement;
    PyExpression[] targets = ((PyAssignmentStatement)element).getTargets();
    if (targets.length == 1 && targets[0] instanceof PySubscriptionExpression subscriptionExpression) {
      if (subscriptionExpression.getOperand() instanceof PyReferenceExpression referenceExpression) {
        final TypeEvalContext context = TypeEvalContext.userInitiated(project, element.getContainingFile());
        final PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
        element = updater.getWritable(referenceExpression.followAssignmentsChain(resolveContext).getElement());
        if (element instanceof PyParenthesizedExpression) {
          final PyExpression expression = ((PyParenthesizedExpression)element).getContainedExpression();
          replaceWithListLiteral(element, (PyTupleExpression)expression);
        }
        else if (element instanceof PyTupleExpression) {
          replaceWithListLiteral(element, (PyTupleExpression)element);
        }
      }
    }
  }

  private static void replaceWithListLiteral(PsiElement element, PyTupleExpression expression) {
    final String expressionText = expression.isEmpty() ? "" :expression.getText();
    final PyExpression literal = PyElementGenerator.getInstance(element.getProject()).
      createExpressionFromText(LanguageLevel.forElement(element),
                               "[" + expressionText + "]");
    element.replace(literal);
  }
}
