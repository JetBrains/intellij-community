/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.spellchecker.inspections.PlainTextSplitter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class XmlTextTokenizer extends Tokenizer<XmlText> {
  @Override
  public void tokenize(@NotNull XmlText element, TokenConsumer consumer) {
    List<Pair<PsiElement,TextRange>> list = InjectedLanguageManager.getInstance(element.getProject()).getInjectedPsiFiles(element);
    if (list != null && list.size() > 0) return;
    final PsiElement[] children = element.getChildren();
    for (PsiElement child : children) {
      if (child instanceof XmlToken && ((XmlToken)child).getTokenType() == XmlTokenType.XML_DATA_CHARACTERS) {
        consumer.consumeToken(child, PlainTextSplitter.getInstance());
      }
    }
  }
}
