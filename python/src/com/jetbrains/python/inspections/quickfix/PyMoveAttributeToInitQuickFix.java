// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.NotNull;

public class PyMoveAttributeToInitQuickFix implements LocalQuickFix {

  public PyMoveAttributeToInitQuickFix() {
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("QFIX.move.attribute");
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    if (!(element instanceof PyTargetExpression)) return;
    final PyTargetExpression targetExpression = (PyTargetExpression)element;

    final PyClass containingClass = targetExpression.getContainingClass();
    final PyAssignmentStatement assignment = PsiTreeUtil.getParentOfType(element, PyAssignmentStatement.class);
    if (containingClass == null || assignment == null) return;

    if (!FileModificationService.getInstance().preparePsiElementForWrite(containingClass)) return;

    WriteAction.run(() -> {
      AddFieldQuickFix.addFieldToInit(project, containingClass, ((PyTargetExpression)element).getName(), x -> assignment);
      removeDefinition(assignment);
    });
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  private static void removeDefinition(PyAssignmentStatement assignment) {
    final PyStatementList statementList = PsiTreeUtil.getParentOfType(assignment, PyStatementList.class);
    if (statementList == null) return;
    assignment.delete();
  }
}
