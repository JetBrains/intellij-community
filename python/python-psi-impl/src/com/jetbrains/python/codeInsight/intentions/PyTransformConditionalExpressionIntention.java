// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 * Intention to transform conditional expression into if/else statement
 * For instance,
 *
 * x = a if cond else b
 *
 * into:
 *
 * if cond:
 *    x = a
 * else:
 *    x = b
 */
public final class PyTransformConditionalExpressionIntention extends PyBaseIntentionAction {
  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("INTN.transform.into.if.else.statement");
  }

  @Override
  @NotNull
  public String getText() {
    return PyPsiBundle.message("INTN.transform.into.if.else.statement");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PyFile)) {
      return false;
    }

    PyAssignmentStatement expression =
      PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyAssignmentStatement.class);
    if (expression != null && expression.getAssignedValue() instanceof PyConditionalExpression) {
      return true;
    }
    return false;
  }

  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PyAssignmentStatement assignmentStatement =
          PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyAssignmentStatement.class);
    assert assignmentStatement != null;
    final PyExpression assignedValue =
      assignmentStatement.getAssignedValue();
    if (assignedValue instanceof PyConditionalExpression expression) {
      final PyExpression condition = expression.getCondition();
      final PyExpression falsePart = expression.getFalsePart();
      if (condition != null && falsePart != null) {
        final String truePartText = expression.getTruePart().getText();
        final PyExpression leftHandSideExpression = assignmentStatement.getLeftHandSideExpression();
        if (leftHandSideExpression != null) {
          final String targetText = leftHandSideExpression.getText();
          final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
          final String text = "if " + condition.getText() + ":\n\t" + targetText + " = " + truePartText
                        + "\nelse:\n\t" + targetText + " = " + falsePart.getText();
          final PyIfStatement ifStatement = elementGenerator.createFromText(LanguageLevel.forElement(expression), PyIfStatement.class, text);
          assignmentStatement.replace(ifStatement);
        }
      }
    }
  }

}
