package com.jetbrains.python.refactoring.surround.surrounders.statements;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyReturnStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 28, 2009
 * Time: 6:00:47 PM
 */
public class PyWithReturnSurrounder extends PyStatementSurrounder {
  public boolean isApplicable(@NotNull PsiElement[] elements) {
    return (elements.length == 1) &&
           (elements[0] instanceof PyExpressionStatement) &&
           (PsiTreeUtil.getParentOfType(elements[0], PyFunction.class) != null);
  }

  @Override
  @Nullable
  protected TextRange surroundStatement(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement[] elements)
    throws IncorrectOperationException {
    PyReturnStatement returnStatement =
      PythonLanguage.getInstance().getElementGenerator().createFromText(project, PyReturnStatement.class, "return a");
    PyExpression expression = returnStatement.getExpression();
    assert expression != null;
    PsiElement element = elements[0];
    expression.replace(element);
    element = element.replace(returnStatement);
    element = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(element);
    return element.getTextRange();
  }

  public String getTemplateDescription() {
    return PyBundle.message("surround.with.return.template");
  }
}
