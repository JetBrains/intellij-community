package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: 10.02.2010
 * Time: 17:45:58
 */
public class ReamoveLeadingUQuickFix implements LocalQuickFix {
  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.remove.leading.u");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PyStringLiteralExpression stringLiteralExpression = (PyStringLiteralExpression) descriptor.getPsiElement();
    PyElementGenerator elementGenerator = PythonLanguage.getInstance().getElementGenerator();
    stringLiteralExpression.replace(elementGenerator.createExpressionFromText(project, stringLiteralExpression.getText().substring(1)));
  }
}
