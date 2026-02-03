// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.braces;

import com.intellij.codeInsight.editorActions.QuoteHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes;
import org.jetbrains.annotations.NotNull;

public final class MarkdownQuoteHandler implements QuoteHandler {
  private static final TokenSet QUOTE_TYPES = TokenSet.create(MarkdownTokenTypes.EMPH,
                                                              //MarkdownTokenTypes.TILDE,
                                                              MarkdownTokenTypes.BACKTICK,
                                                              MarkdownTokenTypes.SINGLE_QUOTE,
                                                              MarkdownTokenTypes.DOUBLE_QUOTE,
                                                              MarkdownTokenTypes.CODE_FENCE_START);

  @Override
  public boolean isClosingQuote(HighlighterIterator iterator, int offset) {
    final CharSequence charsSequence = iterator.getDocument().getCharsSequence();
    final TextRange current = getRangeOfThisType(charsSequence, offset);

    final boolean isBacktick = charsSequence.charAt(offset) == '`';
    final boolean seekPrev = isBacktick ||
                             (current.getStartOffset() - 1 >= 0 &&
                              !Character.isWhitespace(charsSequence.charAt(current.getStartOffset() - 1)));

    if (seekPrev) {
      final int prev = locateNextPosition(charsSequence, charsSequence.charAt(offset), current.getStartOffset() - 1, -1);
      if (prev != -1) {
        return getRangeOfThisType(charsSequence, prev).getLength() <= current.getLength();
      }
    }
    return current.getLength() % 2 == 0 && (!isBacktick || offset > (current.getStartOffset() + current.getEndOffset()) / 2);
  }

  @Override
  public boolean isOpeningQuote(HighlighterIterator iterator, int offset) {
    final IElementType tokenType = iterator.getTokenType();

    if (!QUOTE_TYPES.contains(tokenType)) {
      return false;
    }

    final CharSequence chars = iterator.getDocument().getCharsSequence();

    final boolean isBacktick = chars.charAt(offset) == '`';
    if (isBacktick && isClosingQuote(iterator, offset)) {
      return false;
    }

    return getRangeOfThisType(chars, offset).getLength() != 1 ||
           ((offset <= 0 || Character.isWhitespace(chars.charAt(offset - 1)))
            && (offset + 1 >= chars.length() || Character.isWhitespace(chars.charAt(offset + 1))));
  }

  @Override
  public boolean hasNonClosedLiteral(Editor editor, HighlighterIterator iterator, int offset) {
    final CharSequence charsSequence = editor.getDocument().getCharsSequence();
    final TextRange current = getRangeOfThisType(charsSequence, offset);

    final int next = locateNextPosition(charsSequence, charsSequence.charAt(offset), current.getEndOffset(), +1);
    return next == -1 || getRangeOfThisType(charsSequence, next).getLength() < current.getLength();
  }

  @Override
  public boolean isInsideLiteral(HighlighterIterator iterator) {
    return false;
  }

  private static TextRange getRangeOfThisType(@NotNull CharSequence charSequence, int offset) {
    final int length = charSequence.length();
    final char c = charSequence.charAt(offset);

    int l = offset, r = offset;
    while (l - 1 >= 0 && charSequence.charAt(l - 1) == c) {
      l--;
    }
    while (r + 1 < length && charSequence.charAt(r + 1) == c) {
      r++;
    }
    return TextRange.create(l, r + 1);
  }

  private static int locateNextPosition(@NotNull CharSequence haystack, char needle, int from, int dx) {
    while (from >= 0 && from < haystack.length()) {
      final char currentChar = haystack.charAt(from);
      if (currentChar == needle) {
        return from;
      }
      else if (currentChar == '\n') {
        return -1;
      }

      from += dx;
    }
    return -1;
  }
}
