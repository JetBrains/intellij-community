/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.properties.spellchecker;

import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.psi.PsiElement;
import com.intellij.spellchecker.inspections.SplitterFactory;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.Token;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import org.jetbrains.annotations.NotNull;


public class PropertiesSpellcheckingStrategy extends SpellcheckingStrategy {
  @NotNull
  @Override
  public Tokenizer getTokenizer(PsiElement element) {
    if (element instanceof PropertyValueImpl) {
      return new Tokenizer<PropertyValueImpl>() {
        public Token[] tokenize(@NotNull PropertyValueImpl element) {
          return new Token[]{new Token<PropertyValueImpl>(element, SplitterFactory.getInstance().getStringLiteralSplitter())};
        }
      };
    }
    if (element instanceof PropertyImpl) {
      return new Tokenizer<PropertyImpl>() {
        public Token[] tokenize(@NotNull PropertyImpl element) {
          return new Token[]{new Token<PropertyImpl>(element, element.getKey(), true, SplitterFactory.getInstance().getPropertiesSplitter())};
        }
      };
    }
    return super.getTokenizer(element);
  }
}
