package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyNumericLiteralExpression;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: 12.02.2010
 * Time: 18:41:44
 */
public class ReplaceOctalNumericLiteralQuickFix implements LocalQuickFix {
  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.replace.octal.numeric.literal");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PyNumericLiteralExpression numericLiteralExpression = (PyNumericLiteralExpression) descriptor.getPsiElement();
    PyElementGenerator elementGenerator = PythonLanguage.getInstance().getElementGenerator();
    String text = numericLiteralExpression.getText();
    numericLiteralExpression.replace(elementGenerator.createExpressionFromText(project, "0o" + text.substring(1)));
  }
}
