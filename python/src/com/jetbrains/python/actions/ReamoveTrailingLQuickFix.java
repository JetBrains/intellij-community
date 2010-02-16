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
 * Date: 10.02.2010
 * Time: 17:45:58
 */
public class ReamoveTrailingLQuickFix implements LocalQuickFix {
  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.remove.trailing.l");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PyNumericLiteralExpression numericLiteralExpression = (PyNumericLiteralExpression) descriptor.getPsiElement();
    PyElementGenerator elementGenerator = PythonLanguage.getInstance().getElementGenerator();
    String text = numericLiteralExpression.getText();
    numericLiteralExpression.replace(elementGenerator.createExpressionFromText(project, text.substring(0, text.length() - 1)));
  }
}