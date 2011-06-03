package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * QuickFix to replace chained comparisons with more simple version
 * For instance, a < b and b < c  --> a < b < c
 */
public class ChainedComparisonsQuickFix implements LocalQuickFix {
  boolean myIsLeftLeft;
  boolean myIsRightLeft;
  public ChainedComparisonsQuickFix(boolean isLeft, boolean isRight) {
    myIsLeftLeft = isLeft;
    myIsRightLeft = isRight;
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.chained.comparison");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement expression = descriptor.getPsiElement();
    if (expression != null && expression.isWritable()) {
      if (expression instanceof PyBinaryExpression) {
        PyExpression leftExpression = ((PyBinaryExpression)expression).getLeftExpression();
        PyExpression rightExpression = ((PyBinaryExpression)expression).getRightExpression();
        if (rightExpression instanceof PyBinaryExpression && leftExpression instanceof PyBinaryExpression) {
          if (((PyBinaryExpression)expression).getOperator() == PyTokenTypes.AND_KEYWORD) {
            checkOperator((PyBinaryExpression)leftExpression, (PyBinaryExpression)rightExpression, project);
          }
        }
      }
    }
  }

  private void checkOperator(PyBinaryExpression leftExpression,
                                                          PyBinaryExpression rightExpression, Project project) {
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    if (myIsLeftLeft) {
      PyExpression newLeftExpression = invertExpression(leftExpression, elementGenerator);

      if (myIsRightLeft) {
        PyBinaryExpression binaryExpression = elementGenerator.createBinaryExpression(
                    (rightExpression).getPsiOperator().getText(), newLeftExpression,
                                                          getLargeRightExpression(rightExpression, project));
        leftExpression.replace(binaryExpression);
        rightExpression.delete();
      }
      else {
        PsiElement op = rightExpression.getPsiOperator();
        String newOp = invertOperator(op);
        PyBinaryExpression binaryExpression = elementGenerator.createBinaryExpression(
                    newOp, newLeftExpression, rightExpression.getLeftExpression());
        leftExpression.replace(binaryExpression);
        rightExpression.delete();
      }
    }
    else {
      if (myIsRightLeft) {
        PyBinaryExpression binaryExpression = elementGenerator.createBinaryExpression(
                    (rightExpression).getPsiOperator().getText(), leftExpression, getLargeRightExpression(rightExpression, project));
        leftExpression.replace(binaryExpression);
        rightExpression.delete();
      }
      else {
        PsiElement op = rightExpression.getPsiOperator();
        String newOp = invertOperator(op);
        PyBinaryExpression binaryExpression = elementGenerator.createBinaryExpression(
                    newOp, leftExpression, rightExpression.getLeftExpression());
        leftExpression.replace(binaryExpression);
        rightExpression.delete();
      }
    }

  }

  private PyExpression invertExpression(PyBinaryExpression leftExpression, PyElementGenerator elementGenerator) {
    PsiElement op = leftExpression.getPsiOperator();
    PyExpression right = leftExpression.getRightExpression();
    PyExpression left = leftExpression.getLeftExpression();
    if (left instanceof PyBinaryExpression){
      left = invertExpression((PyBinaryExpression)left, elementGenerator);
    }
    String newOp = invertOperator(op);
    return elementGenerator.createBinaryExpression(
                newOp, right, left);
  }

  private String invertOperator(PsiElement op) {
    if (op.getText().equals(">"))
      return "<";
    if (op.getText().equals("<"))
      return ">";
    if (op.getText().equals(">="))
      return "<=";
    if (op.getText().equals("<="))
      return ">=";
    return op.getText();
  }

  static private PyExpression getLargeRightExpression(PyBinaryExpression expression, Project project) {
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    PyExpression left = expression.getLeftExpression();
    PyExpression right = expression.getRightExpression();
    while (left instanceof PyBinaryExpression) {
      right = elementGenerator.createBinaryExpression(expression.getPsiOperator().getText(), ((PyBinaryExpression)left).getRightExpression(),
                                              right);
      left = ((PyBinaryExpression)left).getLeftExpression();
    }
    return right;
  }

}
