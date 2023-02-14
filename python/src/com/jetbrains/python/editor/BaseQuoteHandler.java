// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.editor;

import com.intellij.codeInsight.editorActions.MultiCharQuoteHandler;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.psi.PyStringLiteralCoreUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;


public class BaseQuoteHandler implements MultiCharQuoteHandler {

  private final char[] ourAutoClosingChars; // we add auto-close quotes before these
  private final TokenSet myPlainStringTokens;

  public BaseQuoteHandler(TokenSet tokenSet, char[] autoClosingChars) {
    myPlainStringTokens = tokenSet;
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
    boolean mayBeSingleQuote = offset + 1 >= text.length() || Arrays.binarySearch(ourAutoClosingChars, text.charAt(offset + 1)) >= 0;
    boolean mayBeTripleQuote = offset + 4 >= text.length() || Arrays.binarySearch(ourAutoClosingChars, text.charAt(offset + 4)) >= 0;

    if (mayBeTripleQuote) {
      if (isOpeningTripleQuote(iterator, offset)) return true;
    }
    if (mayBeSingleQuote) {
      if (isOpeningSingleQuote(iterator, offset)) {
        return true;
      }
      if (getOpeningQuotesTokens().contains(iterator.getTokenType())) {
        int start = iterator.getStart();
        if (offset - start <= PyStringLiteralCoreUtil.MAX_PREFIX_LENGTH) {
          if (getLiteralStartOffset(text, start) == offset) return true;
        }
      }
    }
    return false;
  }

  private boolean isOpeningTripleQuote(HighlighterIterator iterator, int offset) {
    final CharSequence text = iterator.getDocument().getCharsSequence();
    char theQuote = text.charAt(offset);

    final IElementType tokenType = iterator.getTokenType();
    // if we're next to two same quotes, auto-close triple quote
    if (getOpeningQuotesTokens().contains(tokenType)) {
      if (
        offset >= 2 &&
        text.charAt(offset - 1) == theQuote &&
        text.charAt(offset - 2) == theQuote &&
        (offset < 3 || text.charAt(offset - 3) != theQuote)
        ) {
        return getLiteralStartOffset(text, iterator.getStart()) == offset - 2;
      }
    }
    return false;
  }

  private static int getLiteralStartOffset(CharSequence text, int start) {
    return PyStringLiteralCoreUtil.getPrefixEndOffset(text, start);
  }

  @Override
  public boolean hasNonClosedLiteral(Editor editor, HighlighterIterator iterator, int offset) {
    final IElementType tokenType = iterator.getTokenType();
    if (!getOpeningQuotesTokens().contains(tokenType)) {
      return false;
    }
    
    final CharSequence chars = iterator.getDocument().getCharsSequence();
    int end = iterator.getEnd();
    if (getLiteralStartOffset(chars, iterator.getStart()) >= end - 1) return true;
    char endSymbol = chars.charAt(end - 1);
    if (endSymbol != '"' && endSymbol != '\'') return true;

    //for triple quoted string
    if (end >= 3 &&
        (endSymbol == chars.charAt(end - 2)) && (chars.charAt(end - 2) == chars.charAt(end - 3)) &&
        (end < 4 || chars.charAt(end - 4) != endSymbol)) {
      return true;
    }

    return false;
  }

  @Override
  public boolean isClosingQuote(HighlighterIterator iterator, int offset) {
    final IElementType tokenType = iterator.getTokenType();
    if (getClosingQuotesTokens().contains(tokenType)) {
      int start = iterator.getStart();
      int end = iterator.getEnd();
      if (end - start >= 1 && offset == end - 1) {
        Document doc = iterator.getDocument();
        if (doc == null) return false;
        CharSequence chars = doc.getCharsSequence();
        if (chars.length() > offset + 1) {
          char ch = chars.charAt(offset + 1);
          if (Arrays.binarySearch(ourAutoClosingChars, ch) < 0) {
            return false;
          }
        }
        return true;
      }
    }

    return false;
  }

  @Nullable
  @Override
  public CharSequence getClosingQuote(@NotNull HighlighterIterator iterator, int offset) {
    Document document = iterator.getDocument();
    CharSequence text = document.getCharsSequence();
    char theQuote = text.charAt(offset - 1);
    // Both isOpeningTripleQuote() and isOpeningQuote() expect iterator to be on the token
    // of the passed offset, not one character to the right. 
    boolean retreated = false;
    if (iterator.getStart() == offset) {
      retreated = true;
      iterator.retreat();
    }
    try {
      if (isOpeningTripleQuote(iterator, offset - 1)) {
        return StringUtil.repeat(String.valueOf(theQuote), 3);
      }
      else if (isOpeningSingleQuote(iterator, offset - 1)) {
        return String.valueOf(theQuote);
      }
      return null;
    }
    finally {
      if (retreated) {
        iterator.advance();
      }
    }
  }

  private boolean isOpeningSingleQuote(@NotNull HighlighterIterator iterator, int offset) {
    if (getOpeningQuotesTokens().contains(iterator.getTokenType())){
      int start = iterator.getStart();
      return offset == start;
    }
    return false;
  }

  @Override
  public boolean isInsideLiteral(HighlighterIterator iterator) {
    return getLiteralContentTokens().contains(iterator.getTokenType());
  }

  @NotNull
  protected TokenSet getOpeningQuotesTokens() {
    return myPlainStringTokens;
  }

  @NotNull
  protected TokenSet getClosingQuotesTokens() {
    return myPlainStringTokens;
  }

  @NotNull
  protected TokenSet getLiteralContentTokens() {
    return myPlainStringTokens;
  }
}
