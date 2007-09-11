package com.intellij.ide.highlighter.custom.impl;

import com.intellij.codeInsight.editorActions.TypedHandler;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.CustomHighlighterTokenType;
import com.intellij.psi.tree.IElementType;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Dec 6, 2004
 * Time: 8:34:21 PM
 * To change this template use File | Settings | File Templates.
 */
class CustomFileTypeQuoteHandler implements TypedHandler.QuoteHandler {
  public boolean isClosingQuote(HighlighterIterator iterator, int offset) {
    final IElementType tokenType = iterator.getTokenType();

    if (tokenType == CustomHighlighterTokenType.STRING ||
        tokenType == CustomHighlighterTokenType.CHARACTER){
      int start = iterator.getStart();
      int end = iterator.getEnd();
      return end - start >= 1 && offset == end - 1;
    }
    return false;
  }

  public boolean isOpeningQuote(HighlighterIterator iterator, int offset) {
    final IElementType tokenType = iterator.getTokenType();

    if (tokenType == CustomHighlighterTokenType.STRING ||
        tokenType == CustomHighlighterTokenType.CHARACTER){
      int start = iterator.getStart();
      return offset == start;
    }
    return false;
  }

  public boolean hasNonClosedLiteral(Editor editor, HighlighterIterator iterator, int offset) {
    try {
      Document doc = editor.getDocument();
      CharSequence chars = doc.getCharsSequence();
      int lineEnd = doc.getLineEndOffset(doc.getLineNumber(offset));

      while (!iterator.atEnd() && iterator.getStart() < lineEnd) {
        IElementType tokenType = iterator.getTokenType();

        if (tokenType == CustomHighlighterTokenType.STRING ||
            tokenType == CustomHighlighterTokenType.CHARACTER) {

          if (iterator.getStart() >= iterator.getEnd() - 1 ||
              chars.charAt(iterator.getEnd() - 1) != '\"' && chars.charAt(iterator.getEnd() - 1) != '\'') {
            return true;
          }
        }
        iterator.advance();
      }
    } finally {
      while(iterator.getStart() != offset) iterator.retreat();
    }

    return false;
  }

  public boolean isInsideLiteral(HighlighterIterator iterator) {
    final IElementType tokenType = iterator.getTokenType();

    return tokenType == CustomHighlighterTokenType.STRING ||
        tokenType == CustomHighlighterTokenType.CHARACTER;
  }
}
