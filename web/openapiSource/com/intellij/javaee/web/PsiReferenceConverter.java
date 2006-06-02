/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.javaee.web;

import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public interface PsiReferenceConverter {

  /**
   *
   * @param psiElement
   * @param soft
   *  @return empty array if the converter cannot get any references
   */
  @NotNull
  PsiReference[] createReferences(PsiElement psiElement, final boolean soft);
}
