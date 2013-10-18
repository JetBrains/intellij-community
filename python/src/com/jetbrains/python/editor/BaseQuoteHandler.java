package com.jetbrains.python.editor;

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

import java.util.Arrays;

/**
 * @author yole
 */
public class BaseQuoteHandler extends SimpleTokenSetQuoteHandler {

  private final char[] ourAutoClosingChars; // we add auto-close quotes before these

  public BaseQuoteHandler(TokenSet tokenSet, char[] autoClosingChars) {
    super(tokenSet);
    ourAutoClosingChars = autoClosingChars;
    Arrays.sort(ourAutoClosingChars);
  }

  @Override
  public boolean isOpeningQuote(HighlighterIterator iterator, int offset) {
    // don't assume an opening quote unless we're in an explicitly "blank" context
    final Document document = iterator.getDocument();
    if (document == null) {
      return false;
    }
    CharSequence text = document.getCharsSequence();
    if (offset + 1 >= text.length() || Arrays.binarySearch(ourAutoClosingChars, text.charAt(offset + 1)) >= 0) {
      char the_quote = text.charAt(offset);
      // if we're next to two same quotes, don't auto-close, the user may want a triple quote
      if (
        offset >= 2 &&
        text.charAt(offset - 1) == the_quote &&
        text.charAt(offset - 2) == the_quote &&
        (offset < 3 || text.charAt(offset - 3) != the_quote)
        ) {
        return false;
      }
      // handle string literal context
      if (super.isOpeningQuote(iterator, offset)) {
        return true;
      }
      if (myLiteralTokenSet.contains(iterator.getTokenType())) {
        int start = iterator.getStart();
        if (offset - start <= 2) {
          if (getLiteralStartOffset(text, start) == offset) return true;
        }
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
    if (getLiteralStartOffset(chars, iterator.getStart()) >= iterator.getEnd() - 1) return true;
    if (chars.charAt(iterator.getEnd() - 1) != '\"' && chars.charAt(iterator.getEnd() - 1) != '\'') return true;
    return false;
  }

  @Override
  public boolean isClosingQuote(HighlighterIterator iterator, int offset) {
    final IElementType tokenType = iterator.getTokenType();
    if (myLiteralTokenSet.contains(tokenType)) {
      int start = iterator.getStart();
      int end = iterator.getEnd();
      if (end - start >= 1 && offset == end - 1) {
        Document doc = iterator.getDocument();
        if (doc == null) return false;
        CharSequence chars = doc.getCharsSequence();
        if (chars.length() > offset + 1) {
          Character ch = chars.charAt(offset + 1);
          if (Arrays.binarySearch(ourAutoClosingChars, ch) < 0)
            return false;
        }
        return true;
      }
    }

    return false;
  }
}
