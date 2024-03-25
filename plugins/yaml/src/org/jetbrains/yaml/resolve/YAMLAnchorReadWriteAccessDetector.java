// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.resolve;

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLAnchor;

public class YAMLAnchorReadWriteAccessDetector extends ReadWriteAccessDetector {
  @Override
  public boolean isReadWriteAccessible(@NotNull PsiElement element) {
    return element instanceof YAMLAnchor;
  }

  @Override
  public boolean isDeclarationWriteAccess(@NotNull PsiElement element) {
    return element instanceof YAMLAnchor;
  }

  @Override
  public @NotNull Access getReferenceAccess(@NotNull PsiElement referencedElement, @NotNull PsiReference reference) {
    return getExpressionAccess(reference.getElement());
  }

  @Override
  public @NotNull Access getExpressionAccess(@NotNull PsiElement expression) {
    return Access.Read;
  }
}
