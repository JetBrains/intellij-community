package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   17.03.2010
 * Time:   19:41:07
 */
public class SimplifyBooleanCheckQuickFix implements LocalQuickFix {
  private static boolean isTrue(PyExpression expression) {
    return "True".equals(expression.getText());
  }

  private static boolean isFalse(PyExpression expression) {
    return "False".equals(expression.getText());
  }

  private static boolean isNull(PyExpression expression) {
    return "0".equals(expression.getText());
  }

  private static boolean isEmpty(PyExpression expression) {
    return "[]".equals(expression.getText());
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.simplify");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    if (element instanceof PyBinaryExpression) {
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      PyBinaryExpression binaryExpression = (PyBinaryExpression)element;
      PyExpression resultExpression;
      final PyExpression leftExpression = binaryExpression.getLeftExpression();
      final PyExpression rightExpression = binaryExpression.getRightExpression();
      boolean positiveCondition = !TokenSet.create(PyTokenTypes.NE, PyTokenTypes.NE_OLD).contains(binaryExpression.getOperator());
      positiveCondition ^= isFalse(leftExpression) || isFalse(rightExpression) || isNull(rightExpression) || isNull(leftExpression)
                           || isEmpty(rightExpression) || isEmpty(leftExpression);
      if (isTrue(leftExpression) || isFalse(leftExpression) || isNull(leftExpression) || isEmpty(leftExpression)) {
        resultExpression = rightExpression;
      } else {
        resultExpression = leftExpression;
      }
      String text = ((positiveCondition) ? "" : "not ") + resultExpression.getText();
      binaryExpression.replace(elementGenerator.createExpressionFromText(text));
    }
  }
}
