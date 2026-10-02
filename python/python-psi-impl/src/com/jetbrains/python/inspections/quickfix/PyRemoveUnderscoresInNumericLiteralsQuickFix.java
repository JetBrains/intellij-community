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
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class PyRemoveUnderscoresInNumericLiteralsQuickFix extends PsiUpdateModCommandQuickFix {

  @Override
  public @Nls @NotNull String getFamilyName() {
    return PyPsiBundle.message("QFIX.NAME.remove.underscores.in.numeric");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    if (element instanceof PyNumericLiteralExpression) {
      final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      final String text = element.getText();

      element.replace(elementGenerator.createExpressionFromText(LanguageLevel.forElement(element), text.replaceAll("_", "")));
    }
  }
}
