// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class PyRemoveAssignmentStatementTargetQuickFix extends PsiUpdateModCommandQuickFix implements HighPriorityAction {
  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("QFIX.NAME.remove.assignment.target");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final PyAssignmentStatement assignmentStatement = PsiTreeUtil.getParentOfType(element, PyAssignmentStatement.class);
    if (assignmentStatement == null) return;
    if (assignmentStatement.getRawTargets().length == 1) {
      final PyExpression expression = assignmentStatement.getAssignedValue();
      if (expression == null) return;
      final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      PyExpressionStatement statement = elementGenerator.createFromText(LanguageLevel.forElement(expression), PyExpressionStatement.class,
                                                                        expression.getText());
      assignmentStatement.replace(statement);
    }
    else {
      PsiElement possibleNextEq = PsiTreeUtil.nextVisibleLeaf(element);
      if (possibleNextEq == null) return;
      assert possibleNextEq.getNode().getElementType() == PyTokenTypes.EQ;
      element.getParent().deleteChildRange(element, possibleNextEq);
    }
  }
}
