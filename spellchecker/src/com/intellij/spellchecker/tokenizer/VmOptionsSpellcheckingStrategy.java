// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.tokenizer;

import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

final class VmOptionsSpellcheckingStrategy extends SuppressibleSpellcheckingStrategy implements DumbAware {
  @Override
  public boolean isMyContext(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    return file != null && file.getName().equals("idea.vmoptions");
  }

  @Override
  public boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String name) {
    return true;
  }

  @Override
  public SuppressQuickFix[] getSuppressActions(@NotNull PsiElement element, @NotNull String name) {
    return SuppressQuickFix.EMPTY_ARRAY;
  }
}
