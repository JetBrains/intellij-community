// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class PyTupleItemAssignmentInspection extends PyInspection {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
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
