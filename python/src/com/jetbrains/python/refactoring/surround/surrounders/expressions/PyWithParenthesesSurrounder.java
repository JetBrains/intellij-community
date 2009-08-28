package com.jetbrains.python.refactoring.surround.surrounders.expressions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyParenthesizedExpression;
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
    PyParenthesizedExpression parenthesesExpression = (PyParenthesizedExpression)PythonLanguage.getInstance().getElementGenerator()
      .createFromText(project, PyExpressionStatement.class, "(a)").getExpression();
    PyExpression expression = parenthesesExpression.getContainedExpression();
    assert expression != null;
    expression.replace(element);
    element = (PyExpression) element.replace(parenthesesExpression);
    element = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(element);
    return element.getTextRange();
  }
}
