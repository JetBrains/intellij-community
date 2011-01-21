package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   06.03.2010
 * Time:   16:16:52
 */
public class ReplaceNotEqOperatorQuickFix implements LocalQuickFix {
  @NotNull
  @Override
  public String getName() {
    return PyBundle.message("INTN.replace.noteq.operator");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.Family.migration.to.python3");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement binaryExpression = descriptor.getPsiElement();

    if (binaryExpression instanceof PyBinaryExpression) {
      PsiElement operator = ((PyBinaryExpression)binaryExpression).getPsiOperator();
      if (operator != null) {
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        operator.replace(elementGenerator.createFromText(LanguageLevel.forElement(binaryExpression), LeafPsiElement.class, "!="));
      }
    }
  }
}
