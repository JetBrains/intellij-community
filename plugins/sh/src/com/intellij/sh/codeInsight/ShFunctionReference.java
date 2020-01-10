// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.codeInsight;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShFunctionReference extends PsiReferenceBase<PsiElement> {
  public ShFunctionReference(@NotNull PsiElement element) {
    super(element, TextRange.create(0, element.getTextLength()));
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    return ResolveCache.getInstance(myElement.getProject()).resolveWithCaching(this, new ShFunctionResolver(), false, false);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ShFunctionReference that = (ShFunctionReference)o;
    if (!myElement.equals(that.getElement())) return false;
    return true;
  }

  @Override
  public int hashCode() {
    return myElement.hashCode();
  }
}
