package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   17.03.2010
 * Time:   19:41:07
 */
public class SimplifyBooleanCheckQuickFix implements LocalQuickFix {
  private PyBinaryExpression myExpression;
  private String myReplacementText;

  public SimplifyBooleanCheckQuickFix(PyBinaryExpression binaryExpression) {
    myExpression = binaryExpression;
    myReplacementText = createReplacementText();
  }

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
    return PyBundle.message("QFIX.simplify.$0", myReplacementText);
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    myExpression.replace(elementGenerator.createExpressionFromText(myReplacementText));
  }

  private String createReplacementText() {
    PyExpression resultExpression;
    final PyExpression leftExpression = myExpression.getLeftExpression();
    final PyExpression rightExpression = myExpression.getRightExpression();
    boolean positiveCondition = !TokenSet.create(PyTokenTypes.NE, PyTokenTypes.NE_OLD).contains(myExpression.getOperator());
    positiveCondition ^= isFalse(leftExpression) || isFalse(rightExpression) || isNull(rightExpression) || isNull(leftExpression)
                         || isEmpty(rightExpression) || isEmpty(leftExpression);
    if (isTrue(leftExpression) || isFalse(leftExpression) || isNull(leftExpression) || isEmpty(leftExpression)) {
      resultExpression = rightExpression;
    } else {
      resultExpression = leftExpression;
    }
    return ((positiveCondition) ? "" : "not ") + resultExpression.getText();
  }
}
