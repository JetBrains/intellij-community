// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh;

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.tree.IElementType;

import static com.intellij.sh.lexer.ShTokenTypes.*;


public class ShQuoteHandler extends SimpleTokenSetQuoteHandler {
  public ShQuoteHandler() {
    super(BAD_CHARACTER, RAW_STRING, OPEN_QUOTE, CLOSE_QUOTE, OPEN_BACKQUOTE, CLOSE_BACKQUOTE);
  }

  @Override
  public boolean isClosingQuote(HighlighterIterator iterator, int offset) {
    final IElementType tokenType = iterator.getTokenType();

    if (tokenType == OPEN_QUOTE || tokenType == OPEN_BACKQUOTE) return false;
    return super.isClosingQuote(iterator, offset);
  }

  @Override
  public boolean isOpeningQuote(HighlighterIterator iterator, int offset) {
    final IElementType tokenType = iterator.getTokenType();

    if (tokenType == CLOSE_QUOTE || tokenType == CLOSE_BACKQUOTE) return false;
    return super.isOpeningQuote(iterator, offset);
  }
}
