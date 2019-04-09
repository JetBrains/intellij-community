package com.intellij.bash;

import com.intellij.bash.lexer.BashTokenTypes;
import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.tree.IElementType;

public class BashQuoteHandler extends SimpleTokenSetQuoteHandler {
  public BashQuoteHandler() {
    super(BashTokenTypes.BAD_CHARACTER, BashTypes.RAW_STRING, BashTypes.QUOTE, BashTypes.BACKQUOTE);
  }

  @Override
  public boolean isClosingQuote(HighlighterIterator iterator, int offset) {
    final IElementType tokenType = iterator.getTokenType();

    if (myLiteralTokenSet.contains(tokenType)) {
      int start = iterator.getStart();
      int end = iterator.getEnd();
      return end - start >= 1 && offset == end - 1;
    }

    return false;
  }
}
