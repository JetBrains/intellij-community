/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.editor;

import com.intellij.codeInsight.editorActions.MultiCharQuoteHandler;
import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * @author yole
 */
public class BaseQuoteHandler extends SimpleTokenSetQuoteHandler implements MultiCharQuoteHandler {

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
    boolean mayBeSingleQuote = offset + 1 >= text.length() || Arrays.binarySearch(ourAutoClosingChars, text.charAt(offset + 1)) >= 0;
    boolean mayBeTripleQuote = offset + 4 >= text.length() || Arrays.binarySearch(ourAutoClosingChars, text.charAt(offset + 4)) >= 0;

    if (mayBeTripleQuote) {
      char theQuote = text.charAt(offset);
      // if we're next to two same quotes, auto-close triple quote
      if (
        offset >= 2 &&
        text.charAt(offset - 1) == theQuote &&
        text.charAt(offset - 2) == theQuote &&
        (offset < 3 || text.charAt(offset - 3) != theQuote)
        ) {
        return true;
      }
    }
    if (mayBeSingleQuote ) {
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

  @Nullable
  @Override
  public CharSequence getClosingQuote(HighlighterIterator iterator, int offset) {
    Document document = iterator.getDocument();
    String text = document.getText();
    char the_quote = text.charAt(offset - 1);
    if (
      offset >= 2 &&
      text.charAt(offset - 2) == the_quote &&
      text.charAt(offset - 3) == the_quote &&
      (offset < 4 || text.charAt(offset - 4) != the_quote)) {
      return StringUtil.repeat(String.valueOf(the_quote), 3);
    }
    else if (super.isOpeningQuote(iterator, offset - 1)) {
      return String.valueOf(the_quote);
    }
    return null;
  }
}
