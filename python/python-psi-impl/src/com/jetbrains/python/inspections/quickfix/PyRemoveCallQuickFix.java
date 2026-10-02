// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class PyRemoveCallQuickFix extends PsiUpdateModCommandQuickFix {
  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("QFIX.NAME.remove.call");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    assert element instanceof PyCallExpression;
    if (element instanceof PyDecorator) {
      element.delete();
    }
    else {
      final PyArgumentList argumentList = ((PyCallExpression)element).getArgumentList();
      assert argumentList != null;
      argumentList.delete();
      final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      // regenerate element for Psi consistency, because it isn't PyCallExpression anymore
      final PyExpression expression = elementGenerator.createExpressionFromText(LanguageLevel.forElement(element), element.getText());
      element.replace(expression);
    }
  }
}
