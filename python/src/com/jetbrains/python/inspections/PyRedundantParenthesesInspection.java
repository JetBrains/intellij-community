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
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.SmartSerializer;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.inspections.quickfix.RedundantParenthesesQuickFix;
import com.jetbrains.python.psi.*;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.jetbrains.python.psi.PyUtil.as;

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

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.redundant.parentheses");
  }

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

  private class Visitor extends PyInspectionVisitor {
    public Visitor(@NotNull ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyParenthesizedExpression(final PyParenthesizedExpression node) {
      final PyExpression expression = node.getContainedExpression();
      if (node.textContains('\n')) return;
      final PyYieldExpression yieldExpression = PsiTreeUtil.getParentOfType(expression, PyYieldExpression.class, false);
      if (yieldExpression != null) return;
      if (expression instanceof PyTupleExpression && myIgnoreTupleInReturn) {
        return;
      }
      if (expression instanceof PyReferenceExpression || expression instanceof PyLiteralExpression) {
        if (myIgnorePercOperator) {
          final PsiElement parent = node.getParent();
          if (parent instanceof PyBinaryExpression) {
            if (((PyBinaryExpression)parent).getOperator() == PyTokenTypes.PERC) return;
          }
        }

        if (node.getParent() instanceof PyPrintStatement) {
          return;
        }
        registerProblem(node, PyBundle.message("QFIX.redundant.parentheses"), new RedundantParenthesesQuickFix());
      }
      else if (node.getParent() instanceof PyIfPart ||
               node.getParent() instanceof PyWhilePart) {
        registerProblem(node, PyBundle.message("QFIX.redundant.parentheses"), new RedundantParenthesesQuickFix());
      }
      else if (node.getParent() instanceof PyReturnStatement) {
        final PyTupleExpression tuple = as(expression, PyTupleExpression.class);
        if (!(tuple != null && ContainerUtil.or(tuple.getElements(), PyStarExpression.class::isInstance))) {
          registerProblem(node, PyBundle.message("QFIX.redundant.parentheses"), new RedundantParenthesesQuickFix());
        }
      }
      else if (expression instanceof PyBinaryExpression) {
        final PyBinaryExpression binaryExpression = (PyBinaryExpression)expression;

        if (node.getParent() instanceof PyPrefixExpression) {
          return;
        }
        if (binaryExpression.getOperator() == PyTokenTypes.AND_KEYWORD ||
            binaryExpression.getOperator() == PyTokenTypes.OR_KEYWORD) {
          final PyExpression leftExpression = binaryExpression.getLeftExpression();
          final PyExpression rightExpression = binaryExpression.getRightExpression();
          if (leftExpression instanceof PyParenthesizedExpression && rightExpression instanceof PyParenthesizedExpression &&
              !(((PyParenthesizedExpression)leftExpression).getContainedExpression() instanceof PyBinaryExpression) &&
              !(((PyParenthesizedExpression)rightExpression).getContainedExpression() instanceof PyBinaryExpression)) {
            registerProblem(node, PyBundle.message("QFIX.redundant.parentheses"), new RedundantParenthesesQuickFix());
          }
        }
      }
      else if (expression instanceof PyParenthesizedExpression) {
        registerProblem(expression, PyBundle.message("QFIX.redundant.parentheses"), new RedundantParenthesesQuickFix());
      }
    }

    @Override
    public void visitPyArgumentList(PyArgumentList node) {
      if (!(node.getParent() instanceof PyClass)) {
        return;
      }
      if (!myIgnoreEmptyBaseClasses && node.getArguments().length == 0) {
        registerProblem(node, PyBundle.message("QFIX.redundant.parentheses"), new RedundantParenthesesQuickFix());
      }
    }
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox("Ignore argument of % operator", "myIgnorePercOperator");
    panel.addCheckbox("Ignore tuples", "myIgnoreTupleInReturn");
    panel.addCheckbox("Ignore empty lists of base classes", "myIgnoreEmptyBaseClasses");
    return panel;
  }
}
