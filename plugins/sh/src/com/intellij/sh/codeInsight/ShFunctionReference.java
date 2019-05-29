// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.codeInsight;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShFunctionReference extends PsiReferenceBase<PsiElement> {

  public ShFunctionReference(@NotNull PsiElement element) {
    super(element, TextRange.create(0, element.getTextLength()));
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    PsiFile file = myElement.getContainingFile();
    if (file == null) return null;

    ShFunctionResolver resolver = new ShFunctionResolver(file, myElement);
    return resolver.resolveElement();
  }
}
