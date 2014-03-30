/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.rest.spellchecker;

import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.spellchecker.inspections.PlainTextSplitter;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import com.jetbrains.rest.RestLanguage;
import com.jetbrains.rest.RestTokenTypes;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class RestSpellcheckerStrategy extends SpellcheckingStrategy {
  private static final Tokenizer<PsiElement> REST_ELEMENT_TOKENIZER = new Tokenizer<PsiElement>() {
    @Override
    public void tokenize(@NotNull PsiElement element, TokenConsumer consumer) {
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
      return EMPTY_TOKENIZER;
    }
    if (element instanceof LeafPsiElement && element.getLanguage() == RestLanguage.INSTANCE) {
      return REST_ELEMENT_TOKENIZER;
    }
    return EMPTY_TOKENIZER;
  }
}
