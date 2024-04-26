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
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.inspections.quickfix.PyReplaceTupleWithListQuickFix;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.types.PyTupleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

public final class PyTupleItemAssignmentInspection extends PyInspection {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  private static class Visitor extends PyInspectionVisitor {

    Visitor(final ProblemsHolder holder, @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitPyAssignmentStatement(@NotNull PyAssignmentStatement node) {
      PyExpression[] targets = node.getTargets();
      if (targets.length == 1 && targets[0] instanceof PySubscriptionExpression subscriptionExpression) {
        if (subscriptionExpression.getOperand() instanceof PyReferenceExpression referenceExpression) {
          PsiElement element = referenceExpression.followAssignmentsChain(getResolveContext()).getElement();
          if (element instanceof PyExpression expression) {
            PyType type = myTypeEvalContext.getType(expression);
            if (type instanceof PyTupleType) {
              registerProblem(node, PyPsiBundle.message("INSP.tuples.never.assign.items"), new PyReplaceTupleWithListQuickFix());
            }
          }
        }
      }
    }
  }
}
