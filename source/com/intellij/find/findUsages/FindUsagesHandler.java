/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.find.findUsages;

import com.intellij.lang.Language;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class FindUsagesHandler {

  public boolean canFindUsages(final PsiElement element) {
    if (element == null) return false;
    if (element instanceof PsiFile) return ((PsiFile)element).getVirtualFile() != null;
    final Language language = element.getLanguage();
    final FindUsagesProvider provider = language.getFindUsagesProvider();
    return provider.canFindUsagesFor(element);
  }

  @NotNull
  public abstract AbstractFindUsagesDialog getFindUsagesDialog(boolean isSingleFile, boolean toShowInNewTab, boolean mustOpenInNewTab);


  @NotNull
  public PsiElement[] getPrimaryElements() {
    return PsiElement.EMPTY_ARRAY;
  }

  @NotNull
  public PsiElement[] getSecondaryElements() {
    return PsiElement.EMPTY_ARRAY;
  }
}
