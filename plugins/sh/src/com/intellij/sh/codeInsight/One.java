// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.codeInsight;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiPolyVariantCachingReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

//public class One extends PsiPolyVariantCachingReference {
//  @NotNull
//  @Override
//  protected ResolveResult[] resolveInner(boolean incompleteCode, @NotNull PsiFile containingFile) {
//    return new ResolveResult[0];
//  }
//
//  @NotNull
//  @Override
//  public PsiElement getElement() {
//    return null;
//  }
//
//  @NotNull
//  @Override
//  public TextRange getRangeInElement() {
//    return null;
//  }
//
//  @NotNull
//  @Override
//  public String getCanonicalText() {
//    return null;
//  }
//
//  @Override
//  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
//    return null;
//  }
//
//  @Override
//  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
//    return null;
//  }
//}
