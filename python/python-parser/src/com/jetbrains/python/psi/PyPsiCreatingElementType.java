// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * An element type that creates the PSI of its node. {@link PyElementType} and the legacy {@code PyStubElementType} of a dialect
 * implement it, and the Python parser definition calls it for every composite node.
 */
public interface PyPsiCreatingElementType {
  @NotNull PsiElement createElement(@NotNull ASTNode node);
}
