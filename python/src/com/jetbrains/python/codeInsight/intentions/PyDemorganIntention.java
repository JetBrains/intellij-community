package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyDemorganIntention extends BaseIntentionAction {
  @NotNull
  @Override
  public String getText() {
    return "DeMorgan Law";
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "DeMorgan Law";
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PyBinaryExpression expression = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyBinaryExpression.class);
    if (expression != null) {
      PyElementType op = expression.getOperator();
      if (op == PyTokenTypes.AND_KEYWORD || op == PyTokenTypes.OR_KEYWORD) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PyBinaryExpression expression = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()),
                                                                PyBinaryExpression.class);
    PyElementType op = expression.getOperator();
    String converted = convertConjunctionExpression(expression, op);
    replaceExpression(converted, expression);
  }

  private static void replaceExpression(String newExpression, PyBinaryExpression expression) {
    PsiElement expressionToReplace = expression;
    String expString = "not(" + newExpression + ')';
    PsiElement parent = expression.getParent().getParent();
    if (isNegation(parent)) {
      expressionToReplace = parent;
      expString = newExpression;
    }
    PyElementGenerator generator = PyElementGenerator.getInstance(expression.getProject());
    PyExpression newCall = generator.createExpressionFromText(expString);
    PsiElement insertedElement = expressionToReplace.replace(newCall);
    // codeStyleManager = expression.getManager().getCodeStyleManager()
    // TODO codeStyleManager.reformat(insertedElement)
  }

  private static String convertConjunctionExpression(PyBinaryExpression exp, PyElementType tokenType) {
    PyExpression lhs = exp.getLeftExpression();
    String lhsText;
    String rhsText;
    if (isConjunctionExpression(lhs, tokenType)) {
      lhsText = convertConjunctionExpression((PyBinaryExpression)lhs, tokenType);
    }
    else {
      lhsText = convertLeafExpression(lhs);
    }

    PyExpression rhs = exp.getRightExpression();
    if (isConjunctionExpression(rhs, tokenType)) {
      rhsText = convertConjunctionExpression((PyBinaryExpression)rhs, tokenType);
    }
    else {
      rhsText = convertLeafExpression(rhs);
    }

    String flippedConjunction = (tokenType == PyTokenTypes.AND_KEYWORD) ? " or " : " and ";
    return lhsText + flippedConjunction + rhsText;
  }

  private static String convertLeafExpression(PyExpression condition) {
    if (isNegation(condition)) {
      PyExpression negated = getNegated(condition);
      if (negated == null) {
        return "";
      }
      return negated.getText();
    }
    else {
      if (condition instanceof PyBinaryExpression)
        return "not(" + condition.getText() + ")";
      return "not " + condition.getText();
    }
  }

  @Nullable
  private static PyExpression getNegated(PyExpression expression) {
    PyExpression operand = ((PyPrefixExpression)expression).getOperand();
    return operand;  // TODO strip ()
  }

  private static boolean isConjunctionExpression(PyExpression expression, PyElementType tokenType) {
    if (expression instanceof PyBinaryExpression) {
      PyElementType operator = ((PyBinaryExpression) expression).getOperator();
      return operator == tokenType;
    }
    return false;
  }

  private static boolean isNegation(PsiElement expression) {
    if (!(expression instanceof PyPrefixExpression)) {
      return false;
    }
    PyElementType op = ((PyPrefixExpression)expression).getOperationSign();
    return op == PyTokenTypes.NOT_KEYWORD;
  }


}
