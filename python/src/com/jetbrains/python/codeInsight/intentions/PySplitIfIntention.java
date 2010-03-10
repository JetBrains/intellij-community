package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   10.03.2010
 * Time:   18:52:52
 */
public class PySplitIfIntention extends BaseIntentionAction {
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.split.if");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiElement element = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyBinaryExpression.class, false);
    if (element == null) {
      return false;
    }

    while (element.getParent() instanceof PyBinaryExpression) {
      element = element.getParent();
    }
    if (((PyBinaryExpression)element).getOperator() != PyTokenTypes.AND_KEYWORD
        || ((PyBinaryExpression) element).getRightExpression() == null) {
      return false;
    }
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PyIfPart)) {
      return false;
    }
    setText(PyBundle.message("INTN.split.if.text"));
    return true;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PyBinaryExpression element = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyBinaryExpression.class);
    while (element.getParent() instanceof PyBinaryExpression) {
      element = (PyBinaryExpression)element.getParent();
    }
    PyIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PyIfStatement.class);
    PyElementGenerator elementGenerator = PythonLanguage.getInstance().getElementGenerator();
    StringBuilder builder = new StringBuilder();
  
    builder.append("if ").append(element.getLeftExpression().getText()).append(":\n");
    builder.append("    if ").append(element.getRightExpression().getText()).append(":");
    appendStatements(ifStatement.getIfPart().getStatementList(), builder);
    final PyIfPart[] elifParts = ifStatement.getElifParts();
    for (PyIfPart elifPart: elifParts) {
      builder.append("\n    elif ").append(elifPart.getCondition().getText()).append(":");
      appendStatements(elifPart.getStatementList(), builder);
    }
    final PyElsePart elsePart = ifStatement.getElsePart();
    if (elsePart != null) {
      builder.append("\n    else:");
      appendStatements(elsePart.getStatementList(), builder);
    }
    ifStatement.getIfPart().replace(elementGenerator.createFromText(project, PyIfStatement.class, builder.toString()));
  }

  private static void appendStatements(PyStatementList statementList, StringBuilder builder) {
    for (PyStatement statement : statementList.getStatements()) {
      builder.append("\n       ").append(statement.getText());
    }
  }
}
