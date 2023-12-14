// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.NotNull;

public class PyMoveAttributeToInitQuickFix extends PsiUpdateModCommandQuickFix {

  public PyMoveAttributeToInitQuickFix() {
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.move.attribute");
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final PsiElement element, @NotNull ModPsiUpdater updater) {
    if (!(element instanceof PyTargetExpression targetExpression)) return;

    final PyClass containingClass = targetExpression.getContainingClass();
    final PyAssignmentStatement assignment = PsiTreeUtil.getParentOfType(element, PyAssignmentStatement.class);
    if (containingClass == null || assignment == null) return;

    AddFieldQuickFix.addFieldToInit(project, containingClass, ((PyTargetExpression)element).getName(), x -> assignment);
    removeDefinition(assignment);
  }

  private static void removeDefinition(PyAssignmentStatement assignment) {
    final PyStatementList statementList = PsiTreeUtil.getParentOfType(assignment, PyStatementList.class);
    if (statementList == null) return;
    assignment.delete();
  }
}
