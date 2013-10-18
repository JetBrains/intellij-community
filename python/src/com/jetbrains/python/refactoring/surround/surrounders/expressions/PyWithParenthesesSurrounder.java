package com.jetbrains.python.refactoring.surround.surrounders.expressions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 27, 2009
 * Time: 6:03:59 PM
 */
public class PyWithParenthesesSurrounder extends PyExpressionSurrounder {
  public String getTemplateDescription() {
    return CodeInsightBundle.message("surround.with.parenthesis.template");
  }

  @Override
  public boolean isApplicable(@NotNull PyExpression elements) {
    return true;
  }

  @Override
  public TextRange surroundExpression(@NotNull Project project, @NotNull Editor editor, @NotNull PyExpression element)
    throws IncorrectOperationException {
    PyParenthesizedExpression parenthesesExpression = (PyParenthesizedExpression)PyElementGenerator.getInstance(project)
      .createFromText(LanguageLevel.getDefault(), PyExpressionStatement.class, "(a)").getExpression();
    PyExpression expression = parenthesesExpression.getContainedExpression();
    assert expression != null;
    expression.replace(element);
    element = (PyExpression) element.replace(parenthesesExpression);
    element = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(element);
    return element.getTextRange();
  }
}
