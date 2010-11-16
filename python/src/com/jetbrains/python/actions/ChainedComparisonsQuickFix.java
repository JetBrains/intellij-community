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
    PsiElement element = descriptor.getPsiElement();
    PyBinaryExpression expression = null;
    if (element != null && element.isWritable()) {
      if (element instanceof PyAssignmentStatement) {
        PyAssignmentStatement statement = (PyAssignmentStatement)element;

        if (statement.getAssignedValue() instanceof PyBinaryExpression) {
          expression = (PyBinaryExpression)statement.getAssignedValue();
        }
      }
      else if(element instanceof PyIfStatement) {
        PyIfStatement statement = (PyIfStatement)element;

        if (statement.getIfPart().getCondition() instanceof PyBinaryExpression) {
          expression = (PyBinaryExpression)statement.getIfPart().getCondition();
        }
      }
      else if(element instanceof PyWhileStatement) {
        PyWhileStatement statement = (PyWhileStatement)element;

        if (statement.getWhilePart().getCondition() instanceof PyBinaryExpression) {
          expression = (PyBinaryExpression)statement.getWhilePart().getCondition();
        }
      }

    }

    if (expression != null) {
      checkBinaryExpression(expression, project);
    }
  }

  private static void checkBinaryExpression(PyBinaryExpression expression, Project project) {
    if (expression.getOperator() == PyTokenTypes.AND_KEYWORD) {
      PyExpression leftExpression = expression.getLeftExpression();
      PyExpression rightExpression = expression.getRightExpression();

      if (leftExpression instanceof PyBinaryExpression && rightExpression instanceof PyBinaryExpression) {
        if (((PyBinaryExpression)leftExpression).getOperator() == ((PyBinaryExpression)rightExpression).getOperator()) {
          PyExpression leftRight = ((PyBinaryExpression)leftExpression).getRightExpression();
          if (leftRight != null) {
            if (leftRight.getText().equals(getSmallLeftExpression((PyBinaryExpression)rightExpression).getText())) {
                PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
                PyBinaryExpression binaryExpression = elementGenerator.createBinaryExpression(
                        ((PyBinaryExpression)leftExpression).getPsiOperator().getText(), leftExpression,
                                                              getLargeRightExpression((PyBinaryExpression)rightExpression, project));
                expression.replace(binaryExpression);
            }
          }
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
