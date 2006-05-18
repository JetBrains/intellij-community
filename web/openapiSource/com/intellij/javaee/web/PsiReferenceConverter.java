/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.javaee.web;

import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public interface PsiReferenceConverter<T> {

  /**
   *
   * @param source
   * @param psiElement
   * @return null if the converter cannot get any references
   */
  @Nullable
  PsiReference[] createReferences(T source, PsiElement psiElement);
}
