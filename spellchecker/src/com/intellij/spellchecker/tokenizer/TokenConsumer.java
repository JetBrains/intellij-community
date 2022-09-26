// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.tokenizer;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.spellchecker.inspections.Splitter;


public abstract class TokenConsumer {
  public void consumeToken(PsiElement element, Splitter splitter) {
    consumeToken(element, false, splitter);
  }

  public void consumeToken(PsiElement element, boolean useRename, Splitter splitter) {
    String text = element.getText();
    consumeToken(element, text, useRename, 0, TextRange.allOf(text), splitter);
  }

  /**
   * @param element      PSI element on which problem descriptor will be set
   * @param text         literal text that will be analyzed by spellchecker
   * @param useRename    whether rename quick fix should be suggested instead of "change to"
   * @param offset       offset inside element that serves as an anchor point for {@code rangeToCheck}
   * @param rangeToCheck range text value corresponds to
   */
  public abstract void consumeToken(PsiElement element,
                                    String text,
                                    boolean useRename,
                                    int offset,
                                    TextRange rangeToCheck,
                                    Splitter splitter);
}
