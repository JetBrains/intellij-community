/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.spellchecker.inspections.Splitter;

/**
 * @author yole
 */
public abstract class TokenConsumer {
  public void consumeToken(PsiElement element, Splitter splitter) {
    consumeToken(element, false, splitter);
  }

  public void consumeToken(PsiElement element, boolean useRename, Splitter splitter) {
    String text = element.getText();
    consumeToken(element, text, useRename, 0, TextRange.allOf(text), splitter);
  }

  public abstract void consumeToken(PsiElement element,
                                    String text,
                                    boolean useRename,
                                    int offset,
                                    TextRange rangeToCheck,
                                    Splitter splitter);
}
