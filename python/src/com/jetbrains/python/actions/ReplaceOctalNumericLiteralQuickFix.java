package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyNumericLiteralExpression;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   06.03.2010
 * Time:   16:50:53
 */
public class ReplaceOctalNumericLiteralQuickFix implements LocalQuickFix {
  @NotNull
  @Override
  public String getName() {
    return PyBundle.message("INTN.replace.octal.numeric.literal");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.Family.migration.to.python3");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement numericLiteralExpression = descriptor.getPsiElement();
    if (numericLiteralExpression instanceof PyNumericLiteralExpression) {
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      String text = numericLiteralExpression.getText();
      numericLiteralExpression.replace(elementGenerator.createExpressionFromText("0o" + text.substring(1)));
    }
  }
}