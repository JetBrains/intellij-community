package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * QuickFix to remove redundant parentheses from if/while/except statement
 */
public class RedundantParenthesesQuickFix implements LocalQuickFix {
  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.redundant.parentheses");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    PsiElement binaryExpression = ((PyParenthesizedExpression)element).getContainedExpression();
    PyBinaryExpression parent = PsiTreeUtil.getParentOfType(element, PyBinaryExpression.class);
    if (binaryExpression instanceof PyBinaryExpression && parent != null) {
      if (!replaceBinaryExpression((PyBinaryExpression)binaryExpression)) {
        element.replace(binaryExpression);
      }
    }
    else {
      while (element instanceof PyParenthesizedExpression) {
        PyExpression expression = ((PyParenthesizedExpression)element).getContainedExpression();
        if (expression != null) {
          element = element.replace(expression);
        }
      }
    }
  }

  private static boolean replaceBinaryExpression(PyBinaryExpression element) {
    PyExpression left = element.getLeftExpression();
    PyExpression right = element.getRightExpression();
    if (left instanceof PyParenthesizedExpression &&
        right instanceof PyParenthesizedExpression) {
      PyExpression leftContained = ((PyParenthesizedExpression)left).getContainedExpression();
      PyExpression rightContained = ((PyParenthesizedExpression)right).getContainedExpression();
      if (leftContained != null && rightContained != null) {
        left.replace(leftContained);
        right.replace(rightContained);
        return true;
      }
    }
    return false;
  }
}
