// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.spellchecker;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.spellchecker.inspections.PlainTextSplitter;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import org.jetbrains.annotations.NotNull;

public class ShIdentifierOwnerTokenizer extends Tokenizer<PsiNameIdentifierOwner> {
  public static final ShIdentifierOwnerTokenizer INSTANCE = new ShIdentifierOwnerTokenizer();

  @Override
  public void tokenize(@NotNull PsiNameIdentifierOwner element, @NotNull TokenConsumer consumer) {
    PsiElement identifier = element.getNameIdentifier();
    if (identifier == null) {
      return;
    }
    PsiElement parent = element;
    final TextRange range = identifier.getTextRange();
    if (range.isEmpty()) return;

    int offset = range.getStartOffset() - parent.getTextRange().getStartOffset();
    if (offset < 0) {
      parent = PsiTreeUtil.findCommonParent(identifier, element);
      offset = range.getStartOffset() - parent.getTextRange().getStartOffset();
    }
    String text = identifier.getText();
    consumer.consumeToken(parent, text, true, offset, TextRange.allOf(text), PlainTextSplitter.getInstance());
  }
}