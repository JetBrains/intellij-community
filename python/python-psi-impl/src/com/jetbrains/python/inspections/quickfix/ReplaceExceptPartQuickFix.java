// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.lang.ASTNode;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyTryExceptStatement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class ReplaceExceptPartQuickFix extends PsiUpdateModCommandQuickFix {
  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("INTN.convert.except.to");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull PsiElement exceptPart, @NotNull ModPsiUpdater updater) {
    if (exceptPart instanceof PyExceptPart) {
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      PsiElement element = ((PyExceptPart)exceptPart).getExceptClass().getNextSibling();
      while (element instanceof PsiWhiteSpace) {
        element = element.getNextSibling();
      }
      assert element != null;
      PyTryExceptStatement newElement =
        elementGenerator.createFromText(LanguageLevel.forElement(exceptPart), PyTryExceptStatement.class,
                                        "try:  pass except a as b:  pass");
      ASTNode node = newElement.getExceptParts()[0].getNode().findChildByType(PyTokenTypes.AS_KEYWORD);
      assert node != null;
      element.replace(node.getPsi());
    }
  }
}
