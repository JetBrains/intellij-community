package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyRaiseStatement;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   06.03.2010
 * Time:   16:50:53
 */
public class ReplaceRaiseStatementIntention implements IntentionAction {
  @NotNull
  public String getText() {
    return PyBundle.message("INTN.replace.raise.statement");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.Family.migration.to.python3");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PyRaiseStatement raiseStatement =
      PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyRaiseStatement.class);
    if (raiseStatement == null) {
      return;
    }
    PyExpression[] expressions = raiseStatement.getExpressions();
    assert expressions != null;
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    String newExpressionText = expressions[0].getText() + "(" + expressions[1].getText() + ")";
    if (expressions.length == 2) {
      raiseStatement.replace(elementGenerator.createFromText(PyRaiseStatement.class, "raise " + newExpressionText));
    } else if (expressions.length == 3) {
      raiseStatement.replace(elementGenerator.createFromText(PyRaiseStatement.class,
                                                             "raise " + newExpressionText + ".with_traceback(" + expressions[2].getText() + ")"));
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}