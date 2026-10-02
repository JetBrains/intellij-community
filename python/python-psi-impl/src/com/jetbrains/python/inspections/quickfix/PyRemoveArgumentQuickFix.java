// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

//TODO: Remove pydoc aswell
@ApiStatus.Internal
public class PyRemoveArgumentQuickFix extends PsiUpdateModCommandQuickFix {

  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("QFIX.NAME.remove.argument");
  }

  @Override
  public void applyFix(final @NotNull Project project, final @NotNull PsiElement element, final @NotNull ModPsiUpdater updater) {
    if (!(element instanceof PyExpression expression)) return;
    final PsiElement nextSibling = PsiTreeUtil.skipWhitespacesForward(expression);
    final PsiElement prevSibling = PsiTreeUtil.skipWhitespacesBackward(expression);
    expression.delete();
    if (nextSibling != null && nextSibling.getNode().getElementType().equals(PyTokenTypes.COMMA)) {
      nextSibling.delete();
      return;
    }
    if (prevSibling != null && prevSibling.getNode().getElementType().equals(PyTokenTypes.COMMA)) {
      prevSibling.delete();
    }
  }
}
