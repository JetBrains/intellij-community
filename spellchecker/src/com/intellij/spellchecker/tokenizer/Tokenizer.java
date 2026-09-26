// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.tokenizer;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public abstract class Tokenizer<T extends PsiElement> {

  public abstract void tokenize(@NotNull T element, @NotNull TokenConsumer consumer);

  public @NotNull TextRange getHighlightingRange(@NotNull PsiElement element, int offset, @NotNull TextRange textRange) {
    return TextRange.from(offset + textRange.getStartOffset(), textRange.getLength());
  }
}
