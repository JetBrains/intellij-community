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
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.inspections.quickfix.StatementEffectFunctionCallQuickFix;
import com.jetbrains.python.inspections.quickfix.StatementEffectIntroduceVariableQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class PyStatementEffectInspection extends PyInspection {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  @Override
  protected boolean isSuppressForCodeFragment() {
    return true;
  }

  public static class Visitor extends PyInspectionVisitor {

    public Visitor(final ProblemsHolder holder, @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitPyExpressionStatement(final @NotNull PyExpressionStatement node) {
      if (ContainerUtil.exists(PyInspectionExtension.EP_NAME.getExtensionList(),
                               extension -> extension.ignoreNoEffectStatement(node))) {
        return;
      }
      final PyExpression expression = node.getExpression();
      if (PsiTreeUtil.hasErrorElements(expression))
        return;
      if (hasEffect(expression)) return;

      // https://twitter.com/gvanrossum/status/112670605505077248
      if (expression instanceof PyStringLiteralExpression) {
        return;
      }

      final PyTryPart tryPart = PsiTreeUtil.getParentOfType(node, PyTryPart.class);
      if (tryPart != null) {
        final PyStatementList statementList = tryPart.getStatementList();
        if (statementList.getStatements().length == 1 && statementList.getStatements()[0] == node) {
          return;
        }
      }
      List<LocalQuickFix> quickFixes = new SmartList<>(getQuickFixesFromExtensions(node));
      if (!(expression instanceof PyReferenceExpression reference) || reference.isQualified()) {
        quickFixes.add(new StatementEffectIntroduceVariableQuickFix());
      }
      registerProblem(expression,
                      PyPsiBundle.message("INSP.statement.effect.statement.seems.to.have.no.effect"),
                      quickFixes.toArray(LocalQuickFix.EMPTY_ARRAY));
    }

    private boolean hasEffect(@Nullable PyExpression expression) {
      if (expression == null) {
        return false;
      }
      if (expression instanceof PyCallExpression || expression instanceof PyYieldExpression) {
        return true;
      }
      else if (expression instanceof PyListCompExpression) {
        if (hasEffect(((PyListCompExpression)expression).getResultExpression())) {
          return true;
        }
      }
      else if (expression instanceof PyBinaryExpression binary) {

        final PyElementType operator = binary.getOperator();
        if (PyTokenTypes.COMPARISON_OPERATIONS.contains(operator)) return false;

        final PyExpression leftExpression = binary.getLeftExpression();
        final PyExpression rightExpression = binary.getRightExpression();
        if (hasEffect(leftExpression) || hasEffect(rightExpression)) return true;

        String method = operator == null ? null : operator.getSpecialMethodName();
        if (method != null) {
          // maybe the op is overridden and may produce side effects, like cout << "hello"
          PyType type = myTypeEvalContext.getType(leftExpression);
          if (type != null &&
              !type.isBuiltin() &&
              type.resolveMember(method, null, AccessDirection.READ, getResolveContext()) != null) {
            return true;
          }
          if (rightExpression != null) {
            type = myTypeEvalContext.getType(rightExpression);
            if (type != null) {
              String rmethod = "__r" + method.substring(2); // __add__ -> __radd__
              if (!type.isBuiltin() && type.resolveMember(rmethod, null, AccessDirection.READ, getResolveContext()) != null) {
                return true;
              }
            }
          }
        }
      }
      else if (expression instanceof PyConditionalExpression conditionalExpression) {
        return hasEffect(conditionalExpression.getTruePart()) || hasEffect(conditionalExpression.getFalsePart());
      }
      else if (expression instanceof PyParenthesizedExpression parenthesizedExpression) {
        return hasEffect(parenthesizedExpression.getContainedExpression());
      }
      else if (expression instanceof PyReferenceExpression referenceExpression) {
        ResolveResult[] results = referenceExpression.getReference(getResolveContext()).multiResolve(true);
        for (ResolveResult res : results) {
          if (res.getElement() instanceof PyFunction) {
            registerProblem(expression,
                            PyPsiBundle.message("INSP.statement.effect.statement.having.no.effect.can.be.replaced.with.function.call"),
                            new StatementEffectFunctionCallQuickFix());
            return true;
          }
        }
      }
      else if (expression instanceof PyTupleExpression) {
        PyExpression[] elements = ((PyTupleExpression)expression).getElements();
        for (PyExpression element : elements) {
          if (hasEffect(element)) {
            return true;
          }
        }
      }
      else if (expression instanceof PyPrefixExpression prefixExpr) {
        return prefixExpr.getOperator() == PyTokenTypes.AWAIT_KEYWORD;
      }
      else if (expression instanceof PyNoneLiteralExpression && ((PyNoneLiteralExpression)expression).isEllipsis()) {
        return true;
      }
      return false;
    }
  }

  @NotNull
  private static List<LocalQuickFix> getQuickFixesFromExtensions(@NotNull PyExpressionStatement expressionStatement) {
    return ContainerUtil.mapNotNull(PyStatementEffectQuickFixProvider.EP_NAME.getExtensionList(),
                                    extension -> extension.getQuickFix(expressionStatement));
  }
}
