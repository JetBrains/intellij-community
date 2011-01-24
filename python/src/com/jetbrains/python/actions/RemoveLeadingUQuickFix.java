package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   06.03.2010
 * Time:   16:50:53
 */
public class RemoveLeadingUQuickFix implements LocalQuickFix {
  @NotNull
  @Override
  public String getName() {
    return PyBundle.message("INTN.remove.leading.u");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.Family.migration.to.python3");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement stringLiteralExpression = descriptor.getPsiElement();
    if (stringLiteralExpression instanceof PyStringLiteralExpression) {
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      stringLiteralExpression.replace(elementGenerator.createExpressionFromText(stringLiteralExpression.getText().substring(1)));
    }
  }
}