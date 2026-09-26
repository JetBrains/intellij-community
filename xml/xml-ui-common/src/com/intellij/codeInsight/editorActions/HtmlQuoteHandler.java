// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import org.jetbrains.annotations.NotNull;

public class HtmlQuoteHandler implements QuoteHandler {
  private final QuoteHandler myBaseQuoteHandler = new XmlQuoteHandler();

  @Override
  public boolean isClosingQuote(@NotNull HighlighterIterator iterator, int offset) {
    if (myBaseQuoteHandler.isClosingQuote(iterator, offset)) return true;
    return false;
  }

  @Override
  public boolean isOpeningQuote(@NotNull HighlighterIterator iterator, int offset) {
    if (myBaseQuoteHandler.isOpeningQuote(iterator, offset)) return true;

    return false;
  }

  @Override
  public boolean hasNonClosedLiteral(@NotNull Editor editor, @NotNull HighlighterIterator iterator, int offset) {
    if (myBaseQuoteHandler.hasNonClosedLiteral(editor,iterator, offset)) return true;

    return false;
  }

  @Override
  public boolean isInsideLiteral(@NotNull HighlighterIterator iterator) {
    if (myBaseQuoteHandler.isInsideLiteral(iterator)) return true;

    return false;
  }
}
