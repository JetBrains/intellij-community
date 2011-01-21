package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyTupleExpression;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   06.03.2010
 * Time:   16:50:53
 */
public class ReplaceListComprehensionsQuickFix implements LocalQuickFix {
  @NotNull
  @Override
  public String getName() {
    return PyBundle.message("INTN.replace.list.comprehensions");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.Family.migration.to.python3");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement expression = descriptor.getPsiElement();
    if (expression instanceof PyTupleExpression) {
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      PyExpressionStatement statement = elementGenerator.createFromText(LanguageLevel.forElement(expression), PyExpressionStatement.class,
                                                                        "(" + expression.getText() + ")");
      expression.replace(statement.getExpression());
    }
  }
}