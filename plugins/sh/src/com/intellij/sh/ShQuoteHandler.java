package com.intellij.sh;

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.sh.lexer.ShTokenTypes;

public class ShQuoteHandler extends SimpleTokenSetQuoteHandler {
  public ShQuoteHandler() {
    super(ShTokenTypes.BAD_CHARACTER, ShTypes.RAW_STRING, ShTokenTypes.OPEN_QUOTE, ShTokenTypes.CLOSE_QUOTE, ShTypes.BACKQUOTE);
  }

  @Override
  public boolean isClosingQuote(HighlighterIterator iterator, int offset) {
    if (iterator.getTokenType() == ShTokenTypes.OPEN_QUOTE) return false;
    return super.isClosingQuote(iterator, offset);
  }

  @Override
  public boolean isOpeningQuote(HighlighterIterator iterator, int offset) {
    if (iterator.getTokenType() == ShTokenTypes.CLOSE_QUOTE) return false;
    return super.isOpeningQuote(iterator, offset);
  }
}
