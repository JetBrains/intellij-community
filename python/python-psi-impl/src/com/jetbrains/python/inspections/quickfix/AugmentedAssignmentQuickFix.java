// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyAugAssignmentStatementImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * User: catherine
 *
 * QuickFix to replace assignment that can be replaced with augmented assignment.
 * for instance, i = i + 1   --> i +=1
 */
public class AugmentedAssignmentQuickFix extends PsiUpdateModCommandQuickFix {
  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.augment.assignment");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull final ModPsiUpdater updater) {
    if (element instanceof PyAssignmentStatement statement && element.isWritable()) {

      final PyExpression target = statement.getLeftHandSideExpression();
      final PyBinaryExpression expression = (PyBinaryExpression)statement.getAssignedValue();
      if (expression == null) return;
      PyExpression leftExpression = expression.getLeftExpression();
      PyExpression rightExpression = expression.getRightExpression();
      if (rightExpression instanceof PyParenthesizedExpression)
        rightExpression = ((PyParenthesizedExpression)rightExpression).getContainedExpression();
      if (target != null && rightExpression != null) {
        final String targetText = target.getText();
        final String rightText = rightExpression.getText();
        if (rightText.equals(targetText)) {
          final PyExpression tmp = rightExpression;
          rightExpression = leftExpression;
          leftExpression = tmp;
        }
        final List<PsiComment> comments = PsiTreeUtil.getChildrenOfTypeAsList(statement, PsiComment.class);

        if ((leftExpression instanceof PyReferenceExpression || leftExpression instanceof PySubscriptionExpression)) {
          if (leftExpression.getText().equals(targetText)) {
            final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
            final StringBuilder stringBuilder = new StringBuilder();
            final PsiElement psiOperator = expression.getPsiOperator();
            if (psiOperator == null) return;
            stringBuilder.append(targetText).append(" ").
                append(psiOperator.getText()).append("= ").append(rightExpression.getText());
            final PyAugAssignmentStatementImpl augAssignment = elementGenerator.createFromText(LanguageLevel.forElement(element),
                                                          PyAugAssignmentStatementImpl.class, stringBuilder.toString());
            for (PsiComment comment : comments)
              augAssignment.add(comment);
            statement.replace(augAssignment);
          }
        }
      }
    }
  }

}
