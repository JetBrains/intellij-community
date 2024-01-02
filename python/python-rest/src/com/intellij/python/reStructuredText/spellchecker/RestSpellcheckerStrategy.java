// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.spellchecker;

import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.spellchecker.inspections.PlainTextSplitter;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import com.intellij.python.reStructuredText.RestLanguage;
import com.intellij.python.reStructuredText.RestTokenTypes;
import org.jetbrains.annotations.NotNull;


public class RestSpellcheckerStrategy extends SpellcheckingStrategy {
  private static final Tokenizer<PsiElement> REST_ELEMENT_TOKENIZER = new Tokenizer<>() {
    @Override
    public void tokenize(@NotNull PsiElement element, @NotNull TokenConsumer consumer) {
      consumer.consumeToken(element, PlainTextSplitter.getInstance());
    }
  };

  @NotNull
  @Override
  public Tokenizer getTokenizer(PsiElement element) {
    IElementType elementType = element.getNode().getElementType();
    if (elementType == RestTokenTypes.FIELD ||
        elementType == RestTokenTypes.CUSTOM_DIRECTIVE ||
        elementType == RestTokenTypes.REST_DJANGO_INJECTION ||
        elementType == RestTokenTypes.REST_INJECTION) {
      return SpellcheckingStrategy.EMPTY_TOKENIZER;
    }
    if (element instanceof LeafPsiElement && element.getLanguage() == RestLanguage.INSTANCE) {
      return REST_ELEMENT_TOKENIZER;
    }
    return SpellcheckingStrategy.EMPTY_TOKENIZER;
  }
}
