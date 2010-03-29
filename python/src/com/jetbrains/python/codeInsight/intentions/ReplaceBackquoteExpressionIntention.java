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
import com.jetbrains.python.psi.PyReprExpression;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   06.03.2010
 * Time:   16:50:53
 */
public class ReplaceBackquoteExpressionIntention implements IntentionAction {
  @NotNull
  public String getText() {
    return PyBundle.message("INTN.replace.backquote.expression");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.Family.migration.to.python3");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PyReprExpression problemElement =
      PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyReprExpression.class);
    if (problemElement == null) {
      return;
    }
    if (problemElement.getExpression() != null) {
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      problemElement.replace(elementGenerator.createExpressionFromText("repr(" + problemElement.getExpression().getText() + ")"));
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}