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
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.inspections.quickfix.ListCreationQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * User :catherine
 */
public final class PyListCreationInspection extends PyInspection {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
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

  @NotNull
  public static List<PyExpressionStatement> collectSubsequentListAppendCalls(@NotNull PyAssignmentStatement assignment) {
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
