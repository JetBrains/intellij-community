package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyAugAssignmentStatementImpl;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * User: catherine
 */
public class AugmentedAssignmentQuickFix implements LocalQuickFix {

  public AugmentedAssignmentQuickFix() {
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.augment.assignment");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();

    if (element != null && element instanceof PyAssignmentStatement && element.isWritable()) {
      PyAssignmentStatement statement = (PyAssignmentStatement)element;

      PyTargetExpression target = ((PyTargetExpression)statement.getLeftHandSideExpression());
      PyBinaryExpression expression = (PyBinaryExpression)statement.getAssignedValue();
      PyExpression leftExpression = expression.getLeftExpression();
      PyExpression rightExpression = expression.getRightExpression();

      if (leftExpression != null && leftExpression instanceof PyReferenceExpression) {
        if (leftExpression.getName().equals(target.getName())) {
          if (rightExpression instanceof PyNumericLiteralExpression ||
                    rightExpression instanceof PyStringLiteralExpression
                          || rightExpression instanceof PyReferenceExpression) {

            PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(target.getName()).append(" ").
                append(expression.getPsiOperator().getText()).append("= ").append(rightExpression.getText());
            PyAugAssignmentStatementImpl augAssignment = elementGenerator.createFromText(LanguageLevel.getDefault(),
                                                          PyAugAssignmentStatementImpl.class, stringBuilder.toString());
            statement.replace(augAssignment);
          }
        }
      }
    }
  }
}
