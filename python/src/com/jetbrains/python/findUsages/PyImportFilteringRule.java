// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.findUsages;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.rules.ImportFilteringRule;
import com.intellij.usages.rules.PsiElementUsage;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyImportStatementBase;
import org.jetbrains.annotations.NotNull;


public final class PyImportFilteringRule extends ImportFilteringRule {
  @Override
  public boolean isVisible(@NotNull Usage usage, @NotNull UsageTarget @NotNull [] targets) {
    if (usage instanceof PsiElementUsage) {
      final PsiElement psiElement = ((PsiElementUsage)usage).getElement();
      final PsiFile containingFile = psiElement.getContainingFile();
      if (containingFile instanceof PyFile) {
        // check whether the element is in the import list
        final PyImportStatementBase importStatement = PsiTreeUtil.getParentOfType(psiElement, PyImportStatementBase.class, true);
        return importStatement == null;
      }
    }
    return true;
  }
}
