// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyWithItem;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class PyRemoveWithTargetQuickFix extends PsiUpdateModCommandQuickFix {
  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("QFIX.NAME.remove.with.target");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final PyWithItem withItem = PsiTreeUtil.getParentOfType(element, PyWithItem.class);
    if (withItem == null) return;
    final PyExpression withTarget = withItem.getTarget();
    if (withTarget != element) return;
    final PyExpression withExpr = withItem.getExpression();
    withItem.deleteChildRange(withExpr.getNextSibling(), withItem.getLastChild());
  }
}
