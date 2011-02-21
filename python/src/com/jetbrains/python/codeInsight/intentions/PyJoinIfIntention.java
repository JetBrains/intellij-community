package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * User: catherine
 * Intention to merge the if clauses in the case of nested ifs where only the inner if contains code (the outer if only contains the inner one)
 * For instance,
 * if a:
 *   if b:
 *    # stuff here
 * into
 * if a and b:
 *   #stuff here
 */
public class PyJoinIfIntention extends BaseIntentionAction {
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.join.if");
  }

  @NotNull
  public String getText() {
    return PyBundle.message("INTN.join.if.text");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PyIfStatement expression =
      PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyIfStatement.class);

    PyIfStatement outer = getIfStatement(expression);
    if (outer != null) {
      if (outer.getElsePart() != null || outer.getElifParts().length > 0) return false;
      PyStatement firstStatement = getFirstStatement(outer);
      PyStatementList outerStList = outer.getIfPart().getStatementList();
      if (outerStList != null && outerStList.getStatements().length != 1) return false;
      if (firstStatement instanceof PyIfStatement) {
        final PyIfStatement inner = (PyIfStatement)firstStatement;
        if (inner.getElsePart() != null || inner.getElifParts().length > 0) return false;
        PyStatementList stList = inner.getIfPart().getStatementList();
        if (stList != null)
          if (stList.getStatements().length != 0)
            return true;
      }
    }
    return false;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PyIfStatement expression =
          PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyIfStatement.class);
    PyIfStatement ifStatement = getIfStatement(expression);

    PyStatement firstStatement = getFirstStatement(ifStatement);

    if (firstStatement != null && firstStatement instanceof PyIfStatement) {
      PyExpression condition = ((PyIfStatement)firstStatement).getIfPart().getCondition();
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      PyExpression newCondition = elementGenerator.createExpressionFromText(
                    ifStatement.getIfPart().getCondition().getText() + " and " + condition.getText());
      ifStatement.getIfPart().getCondition().replace(newCondition);

      PyStatementList stList = ((PyIfStatement)firstStatement).getIfPart().getStatementList();
      PyStatementList ifStatementList = ifStatement.getIfPart().getStatementList();

      List<PsiComment> comments = PsiTreeUtil.getChildrenOfTypeAsList(ifStatement.getIfPart(), PsiComment.class);
      comments.addAll(PsiTreeUtil.getChildrenOfTypeAsList(((PyIfStatement)firstStatement).getIfPart(), PsiComment.class));
      comments.addAll(PsiTreeUtil.getChildrenOfTypeAsList(ifStatementList, PsiComment.class));
      comments.addAll(PsiTreeUtil.getChildrenOfTypeAsList(stList, PsiComment.class));

      for (PsiElement comm : comments) {
        ifStatement.getIfPart().addBefore(comm, ifStatementList);
        comm.delete();
      }
      ifStatementList.replace(stList);
    }
  }

  @Nullable
  private static PyStatement getFirstStatement(PyIfStatement ifStatement) {
    PyStatement firstStatement = null;
    if (ifStatement != null) {
      PyStatementList stList = ifStatement.getIfPart().getStatementList();
      if (stList != null) {
        if (stList.getStatements().length != 0) {
          firstStatement = stList.getStatements()[0];
        }
      }
    }
    return firstStatement;
  }

  @Nullable
  private static PyIfStatement getIfStatement(PyIfStatement expression) {
    while (expression != null) {
      PyStatementList stList = expression.getIfPart().getStatementList();
      if (stList != null) {
        if (stList.getStatements().length != 0) {
          PyStatement firstStatement = stList.getStatements()[0];
          if (firstStatement instanceof PyIfStatement) {
            break;
          }
        }
      }
      expression = PsiTreeUtil.getParentOfType(expression, PyIfStatement.class);
    }
    return expression;
  }
}
