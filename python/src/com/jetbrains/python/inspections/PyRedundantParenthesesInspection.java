/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.inspections.quickfix.RedundantParenthesesQuickFix;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * User: catherine
 *
 * Inspection to detect redundant parentheses in if/while statement.
 */
public class PyRedundantParenthesesInspection extends PyInspection {

  public boolean myIgnorePercOperator = false;
  public boolean myIgnoreTupleInReturn = false;
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
    return new Visitor(holder, session, myIgnorePercOperator, myIgnoreTupleInReturn);
  }

  private static class Visitor extends PyInspectionVisitor {
    private final boolean myIgnorePercOperator;
    private final boolean myIgnoreTupleInReturn;

    public Visitor(@NotNull ProblemsHolder holder,
                   @NotNull LocalInspectionToolSession session,
                   boolean ignorePercOperator,
                   boolean ignoreTupleInReturn) {
      super(holder, session);
      myIgnorePercOperator = ignorePercOperator;
      myIgnoreTupleInReturn = ignoreTupleInReturn;
    }

    @Override
    public void visitPyParenthesizedExpression(final PyParenthesizedExpression node) {
      PyExpression expression = node.getContainedExpression();
      if (node.getText().contains("\n")) return;
      PyYieldExpression yieldExpression = PsiTreeUtil.getParentOfType(expression, PyYieldExpression.class, false);
      if (yieldExpression != null) return;
      if (expression instanceof PyReferenceExpression || expression instanceof PyLiteralExpression) {
        if (myIgnorePercOperator) {
          PsiElement parent = node.getParent();
          if (parent instanceof PyBinaryExpression) {
            if (((PyBinaryExpression)parent).getOperator() == PyTokenTypes.PERC) return;
          }
        }
        
        if (node.getParent() instanceof PyPrintStatement)
          return;
        registerProblem(node, "Remove redundant parentheses", new RedundantParenthesesQuickFix());
      }
      else if (node.getParent() instanceof PyReturnStatement && expression instanceof PyTupleExpression && myIgnoreTupleInReturn) {
        return;
      }
      else if (node.getParent() instanceof PyIfPart || node.getParent() instanceof PyWhilePart
                  || node.getParent() instanceof PyReturnStatement) {
          registerProblem(node, "Remove redundant parentheses", new RedundantParenthesesQuickFix());
      }
      else if (expression instanceof PyBinaryExpression) {
        PyBinaryExpression binaryExpression = (PyBinaryExpression)expression;

        if (node.getParent() instanceof PyPrefixExpression)
          return;
        if (binaryExpression.getOperator() == PyTokenTypes.AND_KEYWORD ||
            binaryExpression.getOperator() == PyTokenTypes.OR_KEYWORD) {
          if (binaryExpression.getLeftExpression() instanceof PyParenthesizedExpression &&
            binaryExpression.getRightExpression() instanceof PyParenthesizedExpression) {
            registerProblem(node, "Remove redundant parentheses", new RedundantParenthesesQuickFix());
          }
        }
      }
    }
  }

  @Override
  public JComponent createOptionsPanel() {
    MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox("Ignore argument of % operator", "myIgnorePercOperator");
    panel.addCheckbox("Ignore tuple in return statement", "myIgnoreTupleInReturn");
    return panel;
  }
}
