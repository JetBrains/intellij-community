// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.lexer;

import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyStringLiteralCoreUtil;
import com.jetbrains.python.psi.PyStringLiteralUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Specialized lexer for string literals. To be used as a layer in a LayeredLexer.
 * Mostly handles escapes, differently in byte / unicode / raw strings.
 * Snatched from com.intellij.lexer.StringLiteralLexer and may inherit from it in the future.
 * Lexes the entire string, with u/b/r designator, quotes, and content, thus self-adjusts for the format.
 * User: dcheryasov
 */
public class PyStringLiteralLexer extends PyStringLiteralLexerBase {

  private static final short BEFORE_FIRST_QUOTE = 0; // the initial state; may last during 'u' and 'r' prefixes.
  private static final short AFTER_FIRST_QUOTE = 1;
  private static final short AFTER_LAST_QUOTE = 2;

  private int myState;
  private int myLastState;
  private char myQuoteChar;
  private boolean myIsRaw;
  private boolean myIsTriple;

  /**
   * @param originalLiteralToken the AST node we're layering over.
   */
  public PyStringLiteralLexer(final IElementType originalLiteralToken) {
    super(originalLiteralToken);
  }

  @Override
  protected void handleStart(@NotNull CharSequence buffer, int initialState) {
    myState = initialState;
    myLastState = initialState;
    
    // the following could be parsing steps if we wanted this info as tokens
    final String prefix = PyStringLiteralCoreUtil.getPrefix(buffer, myStart);

    myIsRaw = PyStringLiteralUtil.isRawPrefix(prefix);

    final int quoteOffset = myStart + prefix.length();
    // which quote char?
    char c = buffer.charAt(quoteOffset);
    assert (c == '"') || (c == '\'') : "String must be quoted by single or double quote. Found '" + c + "' in string " + buffer;
    myQuoteChar = c;

    myIsTriple = (buffer.length() > quoteOffset + 2) && (buffer.charAt(quoteOffset + 1) == c) && (buffer.charAt(quoteOffset + 2) == c);
  }

  @Override
  protected boolean isRaw() {
    return myIsRaw;
  }

  @Override
  protected boolean isUnicodeMode() {
    return PyTokenTypes.UNICODE_NODES.contains(myOriginalLiteralToken);
  }

  @Override
  protected boolean isEscapeEnd(int offset) {
    return super.isEscapeEnd(offset) || myBuffer.charAt(offset) == myQuoteChar;
  }

  @Override
  public void advance() {
    myLastState = myState;
    super.advance();
  }

  @Override
  public int getState() {
    return myLastState;
  }

  @Override
  protected int locateToken(int start) {
    if (start == myBufferEnd) {
      myState = AFTER_LAST_QUOTE;
    }
    if (myState == AFTER_LAST_QUOTE) return start; // exhausted
    
    int i = start;
    if (myBuffer.charAt(i) == '\\') {
      LOG.assertTrue(myState == AFTER_FIRST_QUOTE);
      final int end = locateEscapeSequence(i);
      if (end == myBufferEnd) {
        myState = AFTER_LAST_QUOTE;
      }
      return end;
    }
    else { // not a \something
      //LOG.assertTrue(myState == AFTER_FIRST_QUOTE || myBuffer.charAt(i) == myQuoteChar);
      final int quote_limit = myIsTriple ? 3 : 1;
      int qcnt = 0; // count consequent quotes
      while (i < myBufferEnd) { // scan to next \something
        if (myBuffer.charAt(i) == '\\' && !isRaw()) {
          return i;
        }
        if (myState == BEFORE_FIRST_QUOTE && myBuffer.charAt(i) == myQuoteChar) {
          qcnt += 1;
          if (qcnt == quote_limit) {
            myState = AFTER_FIRST_QUOTE;
            qcnt = 0; // for last quote detection in the same pass
          }
        }
        else if (myState == AFTER_FIRST_QUOTE && myBuffer.charAt(i) == myQuoteChar && (!isRaw() || myBuffer.charAt(i-1) != '\\')) { // done?
          qcnt += 1;
          if (qcnt == quote_limit) {
            myState = AFTER_LAST_QUOTE;
            return i + 1; // skip the last remaining quote
          }
        }
        else { // not an escape, not a quote
          qcnt = 0;
        }
        i++;
      }
    }

    return i;
  }
}
