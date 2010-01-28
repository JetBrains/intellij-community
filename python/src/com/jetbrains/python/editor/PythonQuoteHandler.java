package com.jetbrains.python.editor;

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.jetbrains.python.PyTokenTypes;

/**
 * @author yole
 */
public class PythonQuoteHandler extends SimpleTokenSetQuoteHandler {
  public PythonQuoteHandler() {
    super(PyTokenTypes.STRING_LITERAL);
  }

  @Override
  public boolean isOpeningQuote(HighlighterIterator iterator, int offset) {
    if (super.isOpeningQuote(iterator, offset)) {
      return true;
    }
    if (myLiteralTokenSet.contains(iterator.getTokenType())) {
      int start = iterator.getStart();
      if (offset - start <= 2) {
        final CharSequence text = iterator.getDocument().getCharsSequence();
        if (getLiteralStartOffset(text, start) == offset) return true;
      }
    }
    return false;
  }

  private static int getLiteralStartOffset(CharSequence text, int start) {
    char c = Character.toUpperCase(text.charAt(start));
    if (c == 'U' || c == 'B') {
      start++;
      c = Character.toUpperCase(text.charAt(start));
    }
    if (c == 'R') {
      start++;
    }
    return start;
  }

  @Override
  protected boolean isNonClosedLiteral(HighlighterIterator iterator, CharSequence chars) {
    if (getLiteralStartOffset(chars, iterator.getStart()) >= iterator.getEnd() - 1 ||
        chars.charAt(iterator.getEnd() - 1) != '\"' && chars.charAt(iterator.getEnd() - 1) != '\'') {
      return true;
    }
    return false;
  }
}
