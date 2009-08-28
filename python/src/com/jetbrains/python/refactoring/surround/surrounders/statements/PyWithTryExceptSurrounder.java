package com.jetbrains.python.refactoring.surround.surrounders.statements;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyTryExceptStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 28, 2009
 * Time: 6:23:51 PM
 */
public class PyWithTryExceptSurrounder extends PyStatementSurrounder {
  @Override
  @Nullable
  protected TextRange surroundStatement(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement[] elements)
    throws IncorrectOperationException {
    PyTryExceptStatement tryStatement = PythonLanguage.getInstance().getElementGenerator()
      .createFromText(project, PyTryExceptStatement.class, "try:\n    \nexcept Exception:\n");
    final PsiElement parent = elements[0].getParent();
    tryStatement.getTryPart().addRange(elements[0], elements[elements.length - 1]);
    tryStatement = (PyTryExceptStatement)parent.addBefore(tryStatement, elements[0]);
    parent.deleteChildRange(elements[0], elements[elements.length - 1]);

    tryStatement = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(tryStatement);
    if (tryStatement == null) {
      return null;
    }
    return tryStatement.getTextRange();
  }

  public String getTemplateDescription() {
    return PyBundle.message("surround.with.try.except.template");
  }
}
