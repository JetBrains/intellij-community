// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: catherine
 *
 * QuickFix to replace statement that has no effect with function call
 */
public class StatementEffectFunctionCallQuickFix implements LocalQuickFix {
  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.statement.effect");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement expression = descriptor.getPsiElement();
    if (expression != null && expression.isWritable() && expression instanceof PyReferenceExpression) {
      final String expressionText = expression.getText();
      if (PyNames.PRINT.equals(expressionText))
        replacePrint(expression);
      else if (PyNames.EXEC.equals(expressionText))
        replaceExec(expression);
      else
        expression.replace(PyElementGenerator.getInstance(project).createCallExpression(LanguageLevel.forElement(expression),
                                                                                        expressionText));
    }
  }

  private static void replaceExec(@NotNull final PsiElement expression) {
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(expression.getProject());
    final String expressionText = expression.getText();
    final StringBuilder stringBuilder = new StringBuilder(expressionText + " (");

    final PsiElement next = getNextElement(expression);
    if (next == null) {
      stringBuilder.append(")");
      expression.replace(elementGenerator.createFromText(LanguageLevel.forElement(expression), PyElement.class,
                                                         stringBuilder.toString()));
      return;
    }
    final String commentText = getComment(next);
    if (next instanceof PyExpressionStatement) {
      final PyExpression expr = ((PyExpressionStatement)next).getExpression();
      if (expr instanceof PyBinaryExpression) {
        final PsiElement operator = ((PyBinaryExpression)expr).getPsiOperator();
        if (operator instanceof LeafPsiElement && ((LeafPsiElement)operator).getElementType() == PyTokenTypes.IN_KEYWORD) {
          addInArguments(stringBuilder, (PyBinaryExpression)expr);
        }
        else {
          stringBuilder.append(next.getText());
        }
      }
      else if (expr instanceof PyTupleExpression) {
        final PyExpression[] elements = ((PyTupleExpression)expr).getElements();
        if (elements.length > 1) {
          if (elements[0] instanceof PyBinaryExpression) {
            addInArguments(stringBuilder, (PyBinaryExpression)elements[0]);
          }
          stringBuilder.append(", ");
          stringBuilder.append(elements[1].getText());
        }
      }
      else {
        stringBuilder.append(((PyExpressionStatement)next).getExpression().getText());
      }
    }
    else {
      stringBuilder.append(next.getText());
    }
    next.delete();
    stringBuilder.append(")");
    if (commentText != null) {
      stringBuilder.append(commentText);
    }
    expression.replace(elementGenerator.createFromText(LanguageLevel.forElement(expression), PyStatement.class,
                                                       stringBuilder.toString()));
  }

  private static String getComment(@Nullable final PsiElement next) {
    String commentText = null;
    if (next != null) {
      final PsiElement lastChild = next.getLastChild();
      if (lastChild instanceof PsiComment) {
        commentText = lastChild.getText();
      }
    }
    return commentText;
  }

  private static void addInArguments(@NotNull final StringBuilder stringBuilder, @NotNull final PyBinaryExpression binaryExpression) {
    stringBuilder.append(binaryExpression.getLeftExpression().getText());
    stringBuilder.append(", ");
    final PyExpression rightExpression = binaryExpression.getRightExpression();
    if (rightExpression != null)
      stringBuilder.append(rightExpression.getText());
  }

  private static void replacePrint(@NotNull final PsiElement expression) {
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(expression.getProject());
    final String expressionText = expression.getText();
    final StringBuilder stringBuilder = new StringBuilder(expressionText + " (");

    final PsiElement next = getNextElement(expression);
    String commentText = getComment(next);
    if (next != null) {
      final String text = next instanceof PyExpressionStatement ? ((PyExpressionStatement)next).getExpression().getText() : next.getText();
      stringBuilder.append(text);
      if (text.endsWith(",")) stringBuilder.append(" end=' '");
      next.delete();
    }
    stringBuilder.append(")");
    if (commentText != null) {
      stringBuilder.append(commentText);
    }
    expression.replace(elementGenerator.createFromText(LanguageLevel.forElement(expression), PyStatement.class,
                                                       stringBuilder.toString()));
  }

  private static PsiElement getNextElement(@NotNull final PsiElement expression) {
    final PsiElement whiteSpace = expression.getContainingFile().findElementAt(expression.getTextOffset() + expression.getTextLength());
    PsiElement next = null;
    if (whiteSpace instanceof PsiWhiteSpace) {
      final String whiteSpaceText = whiteSpace.getText();
      if (!whiteSpaceText.contains("\n")) {
        next = whiteSpace.getNextSibling();
        while (next instanceof PsiWhiteSpace && whiteSpaceText.contains("\\")) {
          next = next.getNextSibling();
        }
      }
    }
    else
      next = whiteSpace;

    RemoveUnnecessaryBackslashQuickFix.removeBackSlash(next);
    if (whiteSpace != null) whiteSpace.delete();
    return next;
  }
}
