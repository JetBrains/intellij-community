/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.spellchecker.tokenizer;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.spellchecker.inspections.Splitter;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class TokenizerBase<T extends PsiElement> extends Tokenizer<T> {
  public static <T extends PsiElement> TokenizerBase<T> create(Splitter splitter) {
    return new TokenizerBase<>(splitter);
  }
  
  private final Splitter mySplitter;

  public TokenizerBase(Splitter splitter) {
    mySplitter = splitter;
  }

  @Override
  public void tokenize(@NotNull T element, TokenConsumer consumer) {
    if (element instanceof PsiLanguageInjectionHost && InjectedLanguageUtil.hasInjections((PsiLanguageInjectionHost)element)) {
      return;
    }
    consumer.consumeToken(element, mySplitter);
  }
}
