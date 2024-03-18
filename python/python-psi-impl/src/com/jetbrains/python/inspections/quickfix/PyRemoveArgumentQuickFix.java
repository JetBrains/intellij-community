// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.NotNull;

//TODO: Remove pydoc aswell
public class PyRemoveArgumentQuickFix extends PsiUpdateModCommandQuickFix {

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.NAME.remove.argument");
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final PsiElement element, @NotNull final ModPsiUpdater updater) {
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
