// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.inspections.quickfix.ListCreationQuickFix;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * User :catherine
 */
@ApiStatus.Internal
public final class PyListCreationInspection extends PyInspection {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  private static class Visitor extends PyInspectionVisitor {
    Visitor(@Nullable ProblemsHolder holder, @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitPyAssignmentStatement(@NotNull PyAssignmentStatement node) {
      if (!(node.getAssignedValue() instanceof PyListLiteralExpression)) return;
      final PyExpression[] targets = node.getTargets();
      if (targets.length != 1) return;
      final PyExpression target = targets[0];
      final String name = target.getName();
      if (name == null) return;
      List<PyExpressionStatement> appendCalls = collectSubsequentListAppendCalls(node);
      if (!appendCalls.isEmpty()) {
        registerProblem(node, PyPsiBundle.message("INSP.list.creation.this.list.creation.could.be.rewritten.as.list.literal"),
                        new ListCreationQuickFix());
      }
    }
  }

  public static @NotNull List<PyExpressionStatement> collectSubsequentListAppendCalls(@NotNull PyAssignmentStatement assignment) {
    ArrayList<PyExpressionStatement> result = new ArrayList<>();
    final PyExpression[] targets = assignment.getTargets();
    assert targets.length == 1;
    final PyExpression target = targets[0];
    final String name = target.getName();
    assert name != null;
    PyStatement expressionStatement = PsiTreeUtil.getNextSiblingOfType(assignment, PyStatement.class);
    while (expressionStatement instanceof PyExpressionStatement) {
      final PyExpression statement = ((PyExpressionStatement)expressionStatement).getExpression();
      if (!(statement instanceof PyCallExpression callExpression)) break;

      final PyExpression callee = callExpression.getCallee();
      if (!(callee instanceof PyQualifiedExpression)) break;

      final PyExpression qualifier = ((PyQualifiedExpression)callee).getQualifier();
      if (qualifier == null || !name.equals(qualifier.getText())) break;

      final String funcName = ((PyQualifiedExpression)callee).getReferencedName();
      if (!"append".equals(funcName)) break;

      final PyArgumentList argList = callExpression.getArgumentList();
      if (argList != null) {
        // TODO Use proper resolve to the original target here
        if (ContainerUtil.exists(argList.getArguments(), argument -> argument.getText().equals(name))) {
          break;
        }
        result.add((PyExpressionStatement)expressionStatement);
      }
      expressionStatement = PsiTreeUtil.getNextSiblingOfType(expressionStatement, PyStatement.class);
    }
    return result;
  }
}
