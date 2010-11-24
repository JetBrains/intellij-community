package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 */
public class PyJoinIfIntention extends BaseIntentionAction {
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.join.if");
  }

  @NotNull
  public String getText() {
    return PyBundle.message("INTN.join.if");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PyIfStatement expression =
      PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyIfStatement.class);
    while (expression != null) {
      PyStatement firstStatement = expression.getIfPart().getStatementList().getStatements()[0];
      if (firstStatement instanceof PyIfStatement) {
        return true;
      }
      expression = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyIfStatement.class);
    }
    return false;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PyIfStatement expression =
      PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyIfStatement.class);
    PyStatement firstStatement = null;

    while (expression != null) {
      firstStatement = expression.getIfPart().getStatementList().getStatements()[0];
      if (firstStatement instanceof PyIfStatement) {
        break;
      }
      expression = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyIfStatement.class);
    }
    if (firstStatement != null && firstStatement instanceof PyIfStatement) {
      PyExpression condition = ((PyIfStatement)firstStatement).getIfPart().getCondition();
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      PyExpression newCondition = elementGenerator.createExpressionFromText(expression.getIfPart().getCondition().getText() + " and " + condition.getText());
      expression.getIfPart().getCondition().replace(newCondition);

      PyStatementList stList = ((PyIfStatement)firstStatement).getIfPart().getStatementList();
      expression.getIfPart().getStatementList().replace(stList);
    }
  }
}
