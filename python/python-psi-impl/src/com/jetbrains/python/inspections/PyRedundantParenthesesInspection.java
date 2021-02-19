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
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.SmartSerializer;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonUiService;
import com.jetbrains.python.inspections.quickfix.RedundantParenthesesQuickFix;
import com.jetbrains.python.psi.*;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * User: catherine
 * <p>
 * Inspection to detect redundant parentheses in if/while statement.
 */
public class PyRedundantParenthesesInspection extends PyInspection {

  private final SmartSerializer mySerializer = new SmartSerializer();

  public boolean myIgnorePercOperator = false;
  public boolean myIgnoreTupleInReturn = false;
  public boolean myIgnoreEmptyBaseClasses = false;

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    mySerializer.writeExternal(this, node);
  }

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    mySerializer.readExternal(this, node);
  }

  private static boolean isTupleWithUnpacking(@NotNull PyExpression expression) {
    return expression instanceof PyTupleExpression &&
           ContainerUtil.or(((PyTupleExpression)expression).getElements(), PyStarExpression.class::isInstance);
  }

  private static boolean oneElementTuple(@NotNull PyExpression expression) {
    return expression instanceof PyTupleExpression && ((PyTupleExpression)expression).getElements().length == 1;
  }

  private static boolean isYieldFrom(@NotNull PsiElement element) {
    return element instanceof PyYieldExpression && ((PyYieldExpression)element).isDelegating();
  }

  private class Visitor extends PyInspectionVisitor {
    Visitor(@NotNull ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyParenthesizedExpression(final @NotNull PyParenthesizedExpression node) {
      if (node.textContains('\n')) return;
      final PsiElement parent = node.getParent();
      if (parent instanceof PyParenthesizedExpression) return;
      final PyExpression expression = node.getContainedExpression();
      if (expression == null || expression instanceof PyYieldExpression) return;
      if (expression instanceof PyTupleExpression && myIgnoreTupleInReturn) {
        return;
      }
      final LanguageLevel languageLevel = LanguageLevel.forElement(node);
      if (expression instanceof PyReferenceExpression || expression instanceof PyLiteralExpression) {
        if (myIgnorePercOperator) {
          if (parent instanceof PyBinaryExpression) {
            if (((PyBinaryExpression)parent).getOperator() == PyTokenTypes.PERC) return;
          }
        }

        if (expression instanceof PyNumericLiteralExpression &&
            ((PyNumericLiteralExpression)expression).isIntegerLiteral() &&
            parent instanceof PyReferenceExpression) {
          return;
        }

        if (parent instanceof PyPrintStatement) {
          return;
        }
        registerProblem(node, PyPsiBundle.message("QFIX.redundant.parentheses"), new RedundantParenthesesQuickFix());
      }
      else if (parent instanceof PyIfPart ||
               parent instanceof PyWhilePart) {
        registerProblem(node, PyPsiBundle.message("QFIX.redundant.parentheses"), new RedundantParenthesesQuickFix());
      }
      else if (parent instanceof PyReturnStatement || parent instanceof PyYieldExpression) {
        if (!isTupleWithUnpacking(expression) && !oneElementTuple(expression) ||
            languageLevel.isAtLeast(LanguageLevel.PYTHON38) && !isYieldFrom(parent)) {
          registerProblem(node, PyPsiBundle.message("QFIX.redundant.parentheses"), new RedundantParenthesesQuickFix());
        }
      }
      else if (expression instanceof PyBinaryExpression) {
        final PyBinaryExpression binaryExpression = (PyBinaryExpression)expression;

        if (parent instanceof PyPrefixExpression) {
          return;
        }
        if (binaryExpression.getOperator() == PyTokenTypes.AND_KEYWORD ||
            binaryExpression.getOperator() == PyTokenTypes.OR_KEYWORD) {
          final PyExpression leftExpression = binaryExpression.getLeftExpression();
          final PyExpression rightExpression = binaryExpression.getRightExpression();
          if (leftExpression instanceof PyParenthesizedExpression && rightExpression instanceof PyParenthesizedExpression &&
              !(((PyParenthesizedExpression)leftExpression).getContainedExpression() instanceof PyBinaryExpression) &&
              !(((PyParenthesizedExpression)rightExpression).getContainedExpression() instanceof PyBinaryExpression)) {
            registerProblem(node, PyPsiBundle.message("QFIX.redundant.parentheses"), new RedundantParenthesesQuickFix());
          }
        }
      }
      else if (expression instanceof PyParenthesizedExpression) {
        registerProblem(expression, PyPsiBundle.message("QFIX.redundant.parentheses"), new RedundantParenthesesQuickFix());
      }
    }

    @Override
    public void visitPyArgumentList(@NotNull PyArgumentList node) {
      if (!(node.getParent() instanceof PyClass)) {
        return;
      }
      if (!myIgnoreEmptyBaseClasses && node.getArguments().length == 0) {
        registerProblem(node, PyPsiBundle.message("QFIX.redundant.parentheses"), new RedundantParenthesesQuickFix());
      }
    }
  }

  @Override
  public JComponent createOptionsPanel() {
    final PythonUiService uiService = PythonUiService.getInstance();
    final JPanel panel = uiService.createMultipleCheckboxOptionsPanel(this);
    uiService.addCheckboxToOptionsPanel(panel, PyPsiBundle.message("INSP.redundant.parens.ignore.argument.of.operator"), "myIgnorePercOperator");
    uiService.addCheckboxToOptionsPanel(panel, PyPsiBundle.message("INSP.redundant.parens.ignore.tuples"), "myIgnoreTupleInReturn");
    uiService.addCheckboxToOptionsPanel(panel, PyPsiBundle.message("INSP.redundant.parens.ignore.empty.lists.of.base.classes"), "myIgnoreEmptyBaseClasses");
    return panel;
  }
}
