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
  public boolean isClosingQuote(@NotNull HighlighterIterator iterator, int offset) {
    final CharSequence charsSequence = iterator.getDocument().getCharsSequence();
    final TextRange current = getRangeOfThisType(charsSequence, offset);

    final boolean isBacktick = charsSequence.charAt(offset) == '`';
    if (isBacktick) {
      int unclosedBacktickLength = findUnclosedBacktickLength(charsSequence, current.getStartOffset());
      if (unclosedBacktickLength != -1) {
        return unclosedBacktickLength <= current.getLength();
      }
    }
    else if (current.getStartOffset() - 1 >= 0 &&
             !Character.isWhitespace(charsSequence.charAt(current.getStartOffset() - 1))) {
      final int prev = locateNextPosition(charsSequence, charsSequence.charAt(offset), current.getStartOffset() - 1, -1);
      if (prev != -1) {
        return getRangeOfThisType(charsSequence, prev).getLength() <= current.getLength();
      }
    }
    return current.getLength() % 2 == 0 && (!isBacktick || offset > (current.getStartOffset() + current.getEndOffset()) / 2);
  }

  @Override
  public boolean isOpeningQuote(@NotNull HighlighterIterator iterator, int offset) {
    final IElementType tokenType = iterator.getTokenType();

    if (!QUOTE_TYPES.contains(tokenType)) {
      return false;
    }

    final CharSequence chars = iterator.getDocument().getCharsSequence();

    final boolean isBacktick = chars.charAt(offset) == '`';
    if (isBacktick && isClosingQuote(iterator, offset)) {
      return false;
    }

    var current = getRangeOfThisType(chars, offset);
    int position = offset - current.getStartOffset();
    boolean canOpen = isBacktick
                      ? current.getLength() % 2 != 0 && position == current.getLength() / 2
                      : position % 2 == 0;
    return canOpen &&
           (current.getLength() != 1 || !isWordPart(chars, offset - 1) && !isWordPart(chars, offset + 1));
  }

  @Override
  public boolean hasNonClosedLiteral(@NotNull Editor editor, @NotNull HighlighterIterator iterator, int offset) {
    final CharSequence charsSequence = editor.getDocument().getCharsSequence();
    final TextRange current = getRangeOfThisType(charsSequence, offset);

    final int next = locateNextPosition(charsSequence, charsSequence.charAt(offset), current.getEndOffset(), +1);
    return next == -1 || getRangeOfThisType(charsSequence, next).getLength() < current.getLength();
  }

  @Override
  public boolean isInsideLiteral(@NotNull HighlighterIterator iterator) {
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

  private static boolean isWordPart(@NotNull CharSequence chars, int offset) {
    return offset >= 0 && offset < chars.length() && Character.isUnicodeIdentifierPart(chars.charAt(offset));
  }

  private static int findUnclosedBacktickLength(@NotNull CharSequence chars, int offset) {
    int lineStart = offset;
    while (lineStart > 0 && chars.charAt(lineStart - 1) != '\n') {
      lineStart--;
    }

    int unclosedLength = -1;
    int current = lineStart;
    while (current < offset) {
      if (chars.charAt(current) != '`') {
        current++;
        continue;
      }

      int runStart = current;
      while (current < offset && chars.charAt(current) == '`') {
        current++;
      }

      int runLength = current - runStart;
      if (unclosedLength == -1) {
        unclosedLength = runLength;
      }
      else if (runLength >= unclosedLength) {
        unclosedLength = -1;
      }
    }
    return unclosedLength;
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
