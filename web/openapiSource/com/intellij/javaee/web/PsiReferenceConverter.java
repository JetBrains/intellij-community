/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.javaee.web;

import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Can be implemented by {@link com.intellij.util.xml.Converter} instance
 *
 * @author Dmitry Avdeev
 */
public interface PsiReferenceConverter {

  /**
   *
   * @param psiElement underlying element for created references ({@link com.intellij.psi.PsiReference#getElement()})
   * @param soft true if created references should be soft ({@link com.intellij.psi.PsiReference#isSoft()})
   * @return empty array if the converter cannot get any references
   */
  @NotNull
  PsiReference[] createReferences(@NotNull PsiElement psiElement, final boolean soft);
}
