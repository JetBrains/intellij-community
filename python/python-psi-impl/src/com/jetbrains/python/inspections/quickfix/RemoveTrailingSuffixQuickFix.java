// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyNumericLiteralExpression;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class RemoveTrailingSuffixQuickFix extends PsiUpdateModCommandQuickFix {

  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("QFIX.remove.trailing.suffix");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    if (element instanceof PyNumericLiteralExpression numeric) {
      String suffix = numeric.getIntegerLiteralSuffix();
      if (suffix == null) return;
      String text = numeric.getText();
      String newText = text.substring(0, text.length() - suffix.length());
      numeric.replace(
        PyElementGenerator.getInstance(project).createExpressionFromText(LanguageLevel.forElement(numeric), newText));
    }
  }
}
