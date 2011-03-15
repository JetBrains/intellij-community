package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
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
public class PyTransformConditionalExpressionIntention extends BaseIntentionAction {
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.transform.into.if.else.statement");
  }

  @NotNull
  public String getText() {
    return PyBundle.message("INTN.transform.into.if.else.statement");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PyAssignmentStatement expression =
      PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyAssignmentStatement.class);
    if (expression != null && expression.getAssignedValue() instanceof PyConditionalExpression) {
      return true;
    }
    return false;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PyAssignmentStatement assignmentStatement =
          PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyAssignmentStatement.class);
    assert assignmentStatement != null;
    PyConditionalExpression expression =
      (PyConditionalExpression)assignmentStatement.getAssignedValue();
    assert expression != null;
    String condition = expression.getCondition().getText();
    String truePart = expression.getTruePart().getText();
    String falsePart = expression.getFalsePart().getText();
    String target = assignmentStatement.getLeftHandSideExpression().getText();

    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    String text = "if " + condition + ":\n\t" + target + " = " + truePart + "\nelse:\n\t" + target + " = " + falsePart;
    PyIfStatement ifStatement = elementGenerator.createFromText(LanguageLevel.forElement(expression), PyIfStatement.class, text);
    assignmentStatement.replace(ifStatement);
  }

}
