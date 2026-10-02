// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyRaiseStatement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class ReplaceRaiseStatementQuickFix extends PsiUpdateModCommandQuickFix {
  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("INTN.replace.raise.statement");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    if (element instanceof PyRaiseStatement) {
      PyExpression[] expressions = ((PyRaiseStatement)element).getExpressions();
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      String newExpressionText = expressions[0].getText() + "(" + expressions[1].getText() + ")";
      if (expressions.length == 2) {
        element.replace(
          elementGenerator.createFromText(LanguageLevel.forElement(element), PyRaiseStatement.class, "raise " + newExpressionText));
      }
      else if (expressions.length == 3) {
        element.replace(elementGenerator.createFromText(LanguageLevel.forElement(element), PyRaiseStatement.class,
                                                        "raise " +
                                                        newExpressionText +
                                                        ".with_traceback(" +
                                                        expressions[2].getText() +
                                                        ")"));
      }
    }
  }
}
