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
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class PyDemorganIntention extends PyBaseIntentionAction {
  @NotNull
  @Override
  public String getText() {
    return PyPsiBundle.message("INTN.NAME.demorgan.law");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PyFile)) {
      return false;
    }

    final PyBinaryExpression expression = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()),
                                                                      PyBinaryExpression.class);
    if (expression != null) {
      final PyElementType op = expression.getOperator();
      if (op == PyTokenTypes.AND_KEYWORD || op == PyTokenTypes.OR_KEYWORD) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PyBinaryExpression expression = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()),
                                                                      PyBinaryExpression.class);
    assert expression != null;
    final PyElementType op = expression.getOperator();
    assert op != null;
    final String converted = convertConjunctionExpression(expression, op);
    replaceExpression(converted, expression);
  }

  private static void replaceExpression(String newExpression, PyBinaryExpression expression) {
    PsiElement expressionToReplace = expression;
    String expString = "not(" + newExpression + ')';
    final PsiElement parent = expression.getParent().getParent();
    if (isNegation(parent)) {
      expressionToReplace = parent;
      expString = newExpression;
    }
    final PyElementGenerator generator = PyElementGenerator.getInstance(expression.getProject());
    final PyExpression newCall = generator.createExpressionFromText(LanguageLevel.forElement(expression), expString);
    expressionToReplace.replace(newCall);
    // codeStyleManager = expression.getManager().getCodeStyleManager()
    // TODO codeStyleManager.reformat(insertedElement)
  }

  @NotNull
  private static String convertConjunctionExpression(@NotNull PyBinaryExpression exp, @NotNull PyElementType tokenType) {
    final PyExpression lhs = exp.getLeftExpression();
    final String lhsText;
    final String rhsText;
    if (isConjunctionExpression(lhs, tokenType)) {
      lhsText = convertConjunctionExpression((PyBinaryExpression)lhs, tokenType);
    }
    else {
      lhsText = convertLeafExpression(lhs);
    }

    final PyExpression rhs = exp.getRightExpression();
    if (isConjunctionExpression(rhs, tokenType)) {
      rhsText = convertConjunctionExpression((PyBinaryExpression)rhs, tokenType);
    }
    else {
      rhsText = convertLeafExpression(rhs);
    }

    final String flippedConjunction = (tokenType == PyTokenTypes.AND_KEYWORD) ? " or " : " and ";
    return lhsText + flippedConjunction + rhsText;
  }

  @NotNull
  private static String convertLeafExpression(@Nullable PyExpression condition) {
    if (condition == null) {
      return "";
    }
    else if (isNegation(condition)) {
      final PyExpression negated = getNegated(condition);
      if (negated == null) {
        return "";
      }
      return negated.getText();
    }
    else {
      if (condition instanceof PyBinaryExpression) {
        return "not(" + condition.getText() + ")";
      }
      return "not " + condition.getText();
    }
  }

  @Nullable
  private static PyExpression getNegated(@NotNull PyExpression expression) {
    return ((PyPrefixExpression)expression).getOperand();  // TODO strip ()
  }

  private static boolean isConjunctionExpression(@Nullable PyExpression expression, @NotNull PyElementType tokenType) {
    if (expression instanceof PyBinaryExpression) {
      final PyElementType operator = ((PyBinaryExpression)expression).getOperator();
      return operator == tokenType;
    }
    return false;
  }

  private static boolean isNegation(@Nullable PsiElement expression) {
    if (!(expression instanceof PyPrefixExpression)) {
      return false;
    }
    final PyElementType op = ((PyPrefixExpression)expression).getOperator();
    return op == PyTokenTypes.NOT_KEYWORD;
  }
}
