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
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.inspections.quickfix.ComparisonWithNoneQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PyComparisonWithNoneInspection extends PyInspection {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  private static class Visitor extends PyInspectionVisitor {
    Visitor(@Nullable ProblemsHolder holder,
            @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitPyBinaryExpression(@NotNull PyBinaryExpression node) {
      final PyExpression rightExpression = node.getRightExpression();
      if ((rightExpression instanceof PyReferenceExpression && PyNames.NONE.equals(rightExpression.getText())) ||
          rightExpression instanceof PyNoneLiteralExpression) {
        final PyElementType operator = node.getOperator();
        if (operator == PyTokenTypes.EQEQ || operator == PyTokenTypes.NE || operator == PyTokenTypes.NE_OLD) {
          PsiReference reference = node.getReference();
          assert reference != null;
          PsiElement result = reference.resolve();
          if (result == null || PyBuiltinCache.getInstance(node).isBuiltin(result)) {
            registerProblem(node, PyPsiBundle.message("INSP.comparison.with.none.performed.with.equality.operators"), new ComparisonWithNoneQuickFix());
          }
        }
      }
    }
  }
}
