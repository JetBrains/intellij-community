// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;

public class HtmlQuoteHandler implements QuoteHandler {
  private final QuoteHandler myBaseQuoteHandler = new XmlQuoteHandler();

  @Override
  public boolean isClosingQuote(HighlighterIterator iterator, int offset) {
    if (myBaseQuoteHandler.isClosingQuote(iterator, offset)) return true;
    return false;
  }

  @Override
  public boolean isOpeningQuote(HighlighterIterator iterator, int offset) {
    if (myBaseQuoteHandler.isOpeningQuote(iterator, offset)) return true;

    return false;
  }

  @Override
  public boolean hasNonClosedLiteral(Editor editor, HighlighterIterator iterator, int offset) {
    if (myBaseQuoteHandler.hasNonClosedLiteral(editor,iterator, offset)) return true;

    return false;
  }

  @Override
  public boolean isInsideLiteral(HighlighterIterator iterator) {
    if (myBaseQuoteHandler.isInsideLiteral(iterator)) return true;

    return false;
  }
}
