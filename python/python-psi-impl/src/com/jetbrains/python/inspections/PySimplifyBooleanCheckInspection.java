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

import com.google.common.collect.ImmutableList;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.inspections.quickfix.SimplifyBooleanCheckQuickFix;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyConditionalStatementPart;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.PyNoneTypeKt;
import com.jetbrains.python.psi.types.PyUnionType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class PySimplifyBooleanCheckInspection extends PyInspection {
  private static final List<String> COMPARISON_LITERALS = ImmutableList.of("True", "False", "[]");

  public boolean ignoreComparisonToZero = true;

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, ignoreComparisonToZero, PyInspectionVisitor.getContext(session));
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(checkbox("ignoreComparisonToZero", PyPsiBundle.message("INSP.simplify.boolean.check.ignore.comparison.to.zero")));
  }

  private static class Visitor extends PyInspectionVisitor {
    private final boolean myIgnoreComparisonToZero;

    Visitor(@Nullable ProblemsHolder holder,
            boolean ignoreComparisonToZero,
            @NotNull TypeEvalContext context) {
      super(holder, context);
      myIgnoreComparisonToZero = ignoreComparisonToZero;
    }

    @Override
    public void visitPyConditionalStatementPart(@NotNull PyConditionalStatementPart node) {
      super.visitPyConditionalStatementPart(node);
      final PyExpression condition = node.getCondition();
      if (condition != null) {
        condition.accept(new PyBinaryExpressionVisitor(getHolder(), myTypeEvalContext, myIgnoreComparisonToZero));
      }
    }
  }

  private static class PyBinaryExpressionVisitor extends PyInspectionVisitor {
    private final boolean myIgnoreComparisonToZero;

    PyBinaryExpressionVisitor(@Nullable ProblemsHolder holder,
                              @NotNull TypeEvalContext context,
                                     boolean ignoreComparisonToZero) {
      super(holder, context);
      myIgnoreComparisonToZero = ignoreComparisonToZero;
    }

    @Override
    public void visitPyBinaryExpression(@NotNull PyBinaryExpression node) {
      super.visitPyBinaryExpression(node);
      final PyElementType operator = node.getOperator();
      final var leftExpression = node.getLeftExpression();
      final PyExpression rightExpression = node.getRightExpression();
      if (rightExpression == null || rightExpression instanceof PyBinaryExpression ||
          leftExpression instanceof PyBinaryExpression) {
        return;
      }

      final var leftType = myTypeEvalContext.getType(leftExpression);
      final var rightType = myTypeEvalContext.getType(rightExpression);

      final var isIdentity = node.isOperator(PyNames.IS) || node.isOperator("isnot");

      // if no type and `is`, then it's unsafe
      if ((leftType == null || rightType == null) && isIdentity) {
        return;
      }

      // because we are comparing to literal values, there will only ever be a union on one side
      final var unionMembers = (leftType instanceof PyUnionType unionType)
                                 ? unionType.getMembers()
                                 : (rightType instanceof PyUnionType unionType)
                                   ? unionType.getMembers()
                                   : null;

      final var isOptional = unionMembers != null && ContainerUtil.exists(unionMembers, PyNoneTypeKt::isNoneType);

      // if the union is `X | Y | None` or just `X | Y` then it is unsafe to simplify
      if (isOptional && unionMembers.size() > 2 || !isOptional && unionMembers != null) {
        return;
      }

      if (!isIdentity
          && !PyTokenTypes.EQUALITY_OPERATIONS.contains(operator)) {
        return;
      }

      final var compareWithZero = !myIgnoreComparisonToZero && operandsEqualTo(node, Collections.singleton("0"));
      boolean compareWithFalsey = operandsEqualTo(node, ImmutableList.of(PyNames.FALSE, "[]"));

      // 'x is falsey' where `x` is `T | None`, then it is unsafe to simplify
      //  because the falsey value will evaluate to `False` which will be ambiguous with `None`
      if (isOptional && (compareWithFalsey || compareWithZero)) {
        return;
      }

      if (operandsEqualTo(node, COMPARISON_LITERALS) || compareWithZero) {
        registerProblem(node);
      }
    }

    private static boolean operandsEqualTo(@NotNull PyBinaryExpression expr, @NotNull Collection<String> literals) {
      final String leftExpressionText = expr.getLeftExpression().getText();
      final PyExpression rightExpression = expr.getRightExpression();
      final String rightExpressionText = rightExpression != null ? rightExpression.getText() : null;
      for (String literal : literals) {
        if (literal.equals(leftExpressionText) || literal.equals(rightExpressionText)) {
          return true;
        }
      }
      return false;
    }

    private void registerProblem(PyBinaryExpression binaryExpression) {
      registerProblem(binaryExpression, PyPsiBundle.message("INSP.expression.can.be.simplified"), new SimplifyBooleanCheckQuickFix(binaryExpression));
    }
  }
}
