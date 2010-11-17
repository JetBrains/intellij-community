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

  public ChainedComparisonsQuickFix() {
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

  static private void checkOperator(PyBinaryExpression leftExpression,
                                                          PyBinaryExpression rightExpression, Project project) {
    if (leftExpression.getRightExpression() instanceof PyBinaryExpression) {
      checkOperator((PyBinaryExpression)leftExpression.getRightExpression(), rightExpression, project);
    }     
    else if (leftExpression.getOperator() == rightExpression.getOperator() &&
                PyTokenTypes.RELATIONAL_OPERATIONS.contains(leftExpression.getOperator())) {
      PyExpression leftRight = leftExpression.getRightExpression();
      if (leftRight != null) {
        if (leftRight.getText().equals(getSmallLeftExpression(rightExpression).getText())) {
          PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
          PyBinaryExpression binaryExpression = elementGenerator.createBinaryExpression(
                  (leftExpression).getPsiOperator().getText(), leftExpression,
                                                        getLargeRightExpression(rightExpression, project));
          leftExpression.replace(binaryExpression);
          rightExpression.delete();
        }
      }
    }
  }

  static private PyExpression getSmallLeftExpression(PyBinaryExpression expression) {
    PyExpression result = expression;
    while (result instanceof PyBinaryExpression) {
      result = ((PyBinaryExpression)result).getLeftExpression();
    }
    return result;
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
