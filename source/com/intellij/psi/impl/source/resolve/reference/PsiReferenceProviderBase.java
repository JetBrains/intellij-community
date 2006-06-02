/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.psi.impl.source.resolve.reference;

import org.jetbrains.annotations.NotNull;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.scope.PsiScopeProcessor;

/**
 * @author Dmitry Avdeev
 */
public abstract class PsiReferenceProviderBase implements PsiReferenceProvider {

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    return PsiReference.EMPTY_ARRAY;
  }

  @NotNull
  public PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
    return PsiReference.EMPTY_ARRAY;
  }

  public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {

  }
}
