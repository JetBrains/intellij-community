package com.jetbrains.python.inspections.quickfix;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyStarExpression;
import org.jetbrains.annotations.NotNull;

public class PyReplaceStarByUnpackQuickFix extends PsiUpdateModCommandQuickFix {
  @NotNull
  @Override
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.replace.star.by.unpack");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    if (!(element instanceof PyStarExpression starExpression)) return;

    PyExpression expression = starExpression.getExpression();
    if (expression == null) return;
    PsiFile file = starExpression.getContainingFile();

    PyUnpackTypeVarTupleQuickFix.replaceToTypingExtensionsUnpack(starExpression, expression, file, project);
  }
}
