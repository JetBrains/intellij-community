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
package com.jetbrains.python.lexer;

import com.intellij.lexer.LexerBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyStringLiteralUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Specialized lexer for string literals. To be used as a layer in a LayeredLexer.
 * Mostly handles escapes, differently in byte / unicode / raw strings.
 * Snatched from com.intellij.lexer.StringLiteralLexer and may inherit from it in the future.
 * Lexes the entire string, with u/b/r designator, quotes, and content, thus self-adjusts for the format.
 * User: dcheryasov
 */
public class PyStringLiteralLexer extends LexerBase {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.lexer.PyStringLiteralLexer");

  private static final short BEFORE_FIRST_QUOTE = 0; // the initial state; may last during 'u' and 'r' prefixes.
  private static final short AFTER_FIRST_QUOTE = 1;
  private static final short AFTER_LAST_QUOTE = 2;

  private CharSequence myBuffer;
  private int myStart;
  private int myEnd;
  private int myState;
  private int myLastState;
  private int myBufferEnd;
  private char myQuoteChar;

  private boolean myIsRaw;
  private boolean myIsTriple;
  private boolean myIsFormatted;
  private final IElementType myOriginalLiteralToken;
  private boolean mySeenEscapedSpacesOnly;


  /**
   * @param originalLiteralToken the AST node we're layering over.
   */
  public PyStringLiteralLexer(final IElementType originalLiteralToken) {
    myOriginalLiteralToken = originalLiteralToken;
  }

  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myBuffer = buffer;
    myStart = startOffset;
    myState = initialState;
    myLastState = initialState;
    mySeenEscapedSpacesOnly = true;
    myBufferEnd = endOffset;

    // the following could be parsing steps if we wanted this info as tokens
    final String prefix = PyStringLiteralUtil.getPrefix(buffer, myStart);

    myIsFormatted = PyStringLiteralUtil.isFormattedPrefix(prefix);
    myIsRaw = PyStringLiteralUtil.isRawPrefix(prefix);

    final int quoteOffset = myStart + prefix.length();
    // which quote char?
    char c = buffer.charAt(quoteOffset);
    assert (c == '"') || (c == '\'') : "String must be quoted by single or double quote. Found '" + c + "' in string " + buffer;
    myQuoteChar = c;

    myIsTriple = (buffer.length() > quoteOffset + 2) && (buffer.charAt(quoteOffset + 1) == c) && (buffer.charAt(quoteOffset + 2) == c);

    // calculate myEnd at last
    myEnd = locateToken(myStart);
  }

  public int getState() {
    return myLastState;
  }

  public IElementType getTokenType() {
    if (myStart >= myEnd) return null;

    // skip non-escapes immediately
    if (myBuffer.charAt(myStart) != '\\' || (myIsRaw && (!isUnicodeMode() || !nextIsUnicodeEscape()))) {
      mySeenEscapedSpacesOnly = false;
      return myOriginalLiteralToken;
    }

    // from here on, only escapes
    if (myStart + 1 >= myEnd) return StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN; // escape ends too early
    char nextChar = myBuffer.charAt(myStart + 1);
    mySeenEscapedSpacesOnly &= nextChar == ' ';
    if ((nextChar == '\n' || nextChar == ' ' && (mySeenEscapedSpacesOnly || isTrailingSpace(myStart+2)))) {
      return StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN; // escaped EOL
    }
    if (nextChar == 'u' || nextChar == 'U') {
      if (isUnicodeMode()) {
        final int width = nextChar == 'u'? 4 : 8; // is it uNNNN or Unnnnnnnn
        for(int i = myStart + 2; i < myStart + width + 2; i++) {
          if (i >= myEnd || !StringUtil.isHexDigit(myBuffer.charAt(i))) return StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN;
        }
        return StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN;
      }
      else return myOriginalLiteralToken; // b"\u1234" is just b"\\u1234", nothing gets escaped
    }

    if (nextChar == 'x') { // \xNN is allowed both in bytes and unicode.
      for(int i = myStart + 2; i < myStart + 4; i++) {
        if (i >= myEnd || !StringUtil.isHexDigit(myBuffer.charAt(i))) return StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN;
      }
      return StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN;
    }

    if (nextChar == 'N' && isUnicodeMode()) {
      int i = myStart+2;
      if (i >= myEnd || myBuffer.charAt(i) != '{') return StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN;
      i++;
      while(i < myEnd && myBuffer.charAt(i) != '}') i++;
      if (i >= myEnd) return StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN;
      return StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN;
    }

    switch (nextChar) {
      case 'a':
      case 'b':
      case 'f':
      case 'n':
      case 'r':
      case 't':
      case 'v':
      case '\'':
      case '\"':
      case '\\':
      case '0':
      case '1':
      case '2':
      case '3':
      case '4':
      case '5':
      case '6':
      case '7': return StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN;
    }

    // other unrecognized escapes are just part of string, not an error
    return myOriginalLiteralToken;
  }

  private boolean nextIsUnicodeEscape() {
    if (myStart + 1 < myEnd) {
      char nextChar = myBuffer.charAt(myStart + 1);
      return nextChar == 'u' || nextChar == 'U';
    }
    return false;
  }

  private boolean isUnicodeMode() {
    return PyTokenTypes.UNICODE_NODES.contains(myOriginalLiteralToken);
  }

  // all subsequent chars are escaped spaces
  private boolean isTrailingSpace(final int start) {
    for (int i=start; i<myBufferEnd; i+=2) {
      final char c = myBuffer.charAt(i);
      if (c != '\\') return false;
      if (i == myBufferEnd-1) return false;
      if (myBuffer.charAt(i+1) != ' ') return false;
    }
    return true;
  }

  public int getTokenStart() {
    assert myStart < myEnd || (myStart == myEnd && myEnd == myBufferEnd);
    return myStart;
  }

  public int getTokenEnd() {
    if (!(myStart < myEnd || (myStart == myEnd && myEnd == myBufferEnd))) {
      LOG.error("myStart=" + myStart + " myEnd="+ myEnd + " myBufferEnd=" + myBufferEnd + " text=" + myBuffer.subSequence(myStart, myBufferEnd));
    }
    return myEnd;
  }

  private int locateToken(int start) {
    if (start == myBufferEnd) {
      myState = AFTER_LAST_QUOTE;
    }
    if (myState == AFTER_LAST_QUOTE) return start; // exhausted
    int i = start;
    if (myBuffer.charAt(i) == '\\') {
      LOG.assertTrue(myState == AFTER_FIRST_QUOTE);
      i++;
      if (myIsRaw) return i;
      if (i == myBufferEnd) {
        myState = AFTER_LAST_QUOTE;
        return i;
      }

      // is octal?
      if (myBuffer.charAt(i) >= '0' && myBuffer.charAt(i) <= '7') {
        char first = myBuffer.charAt(i);
        i++;
        if (i < myBufferEnd && myBuffer.charAt(i) >= '0' && myBuffer.charAt(i) <= '7') {
          i++;
          if (i < myBufferEnd && first <= '3' && myBuffer.charAt(i) >= '0' && myBuffer.charAt(i) <= '7') {
            i++;
          }
        }
        return i;
      }

      // \xNN byte escape
      if (myBuffer.charAt(i) == 'x') {
        i++;
        for (; i < start + 4; i++) {
          if (i == myBufferEnd || myBuffer.charAt(i) == '\n' || myBuffer.charAt(i) == myQuoteChar || myBuffer.charAt(i) == '\\') {
            return i;
          }
        }
        return i;
      }

      // unicode escape
      if (myBuffer.charAt(i) == 'u' || myBuffer.charAt(i) == 'U') {
        final int width = myBuffer.charAt(i) == 'u'? 4 : 8; // is it uNNNN or Unnnnnnnn
        i++;
        for (; i < start + width + 2; i++) {
          if (i == myBufferEnd || myBuffer.charAt(i) == '\n' || myBuffer.charAt(i) == myQuoteChar || myBuffer.charAt(i) == '\\') {
            return i;
          }
        }
        return i;
      }

      if (myBuffer.charAt(i) == 'N' && isUnicodeMode()) {
        i++;
        while(i < myBufferEnd && myBuffer.charAt(i) != '}' && myBuffer.charAt(i) != '\\') {
          i++;
        }
        if (i < myBufferEnd && myBuffer.charAt(i) == '}') {
          i++;
        }
        return i;
      }

      else {
        return i + 1;
      }
    }
    else { // not a \something
      //LOG.assertTrue(myState == AFTER_FIRST_QUOTE || myBuffer.charAt(i) == myQuoteChar);
      final int quote_limit = myIsTriple ? 3 : 1;
      int qcnt = 0; // count consequent quotes
      while (i < myBufferEnd) { // scan to next \something
        if (myBuffer.charAt(i) == '\\' && !myIsRaw) {
          return i;
        }
        if (myState == BEFORE_FIRST_QUOTE && myBuffer.charAt(i) == myQuoteChar) {
          qcnt += 1;
          if (qcnt == quote_limit) {
            myState = AFTER_FIRST_QUOTE;
            qcnt = 0; // for last quote detection in the same pass
          }
        }
        else if (myState == AFTER_FIRST_QUOTE && myBuffer.charAt(i) == myQuoteChar && (!myIsRaw || myBuffer.charAt(i-1) != '\\')) { // done?
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

  public void advance() {
    myLastState = myState;
    myStart = myEnd;
    myEnd = locateToken(myStart);
    if (! (myStart < myEnd || (myStart == myEnd && myEnd == myBufferEnd))) {
      LOG.warn("Inconsistent: start " + myStart + ", end " + myEnd + ", buf end " + myBufferEnd);
    }
    //assert myStart < myEnd || (myStart == myEnd && myEnd == myBufferEnd) : "Inconsistent: start " + myStart + ", end " + myEnd + ", buf end " + myBufferEnd;
  }

  @NotNull
  public CharSequence getBufferSequence() {
    return myBuffer;
  }

  public int getBufferEnd() {
    return myBufferEnd;
  }
}
