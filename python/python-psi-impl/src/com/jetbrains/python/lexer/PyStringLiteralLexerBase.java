// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.lexer;

import com.intellij.lexer.LexerBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public abstract class PyStringLiteralLexerBase extends LexerBase {
  protected static final Logger LOG = Logger.getInstance(PyStringLiteralLexer.class);
  protected final IElementType myOriginalLiteralToken;
  protected CharSequence myBuffer;
  protected int myBufferEnd;
  protected int myStart;
  protected int myEnd;
  protected int myBaseLexerState;
  private boolean mySeenEscapedSpacesOnly;

  public PyStringLiteralLexerBase(final IElementType originalLiteralToken) {
    myOriginalLiteralToken = originalLiteralToken;
  }

  @Override
  public final void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myBuffer = buffer;
    myStart = startOffset;
    mySeenEscapedSpacesOnly = true;
    myBufferEnd = endOffset;
    myBaseLexerState = initialState;

    handleStart(buffer, initialState);

    // calculate myEnd at last
    myEnd = locateToken(myStart);
  }

  protected void handleStart(@NotNull CharSequence buffer, int initialState) {}

  protected abstract boolean isRaw();

  protected abstract boolean isUnicodeMode();

  @Override
  public IElementType getTokenType() {
    if (myStart >= myEnd) return null;

    // skip non-escapes immediately
    if (!isEscape()) {
      mySeenEscapedSpacesOnly = false;
      return myOriginalLiteralToken;
    }

    // from here on, only escapes
    return getEscapeSequenceType();
  }

  @NotNull
  public IElementType getEscapeSequenceType() {
    if (myStart + 1 >= myEnd) return StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN; // escape ends too early
    char nextChar = myBuffer.charAt(myStart + 1);
    mySeenEscapedSpacesOnly &= nextChar == ' ';
    if ((nextChar == '\n' || nextChar == ' ' && (mySeenEscapedSpacesOnly || isTrailingSpace(myStart + 2)))) {
      return StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN; // escaped EOL
    }
    if (nextChar == 'u' || nextChar == 'U') {
      if (isUnicodeMode()) {
        final int width = nextChar == 'u' ? 4 : 8; // is it uNNNN or Unnnnnnnn
        for (int i = myStart + 2; i < myStart + width + 2; i++) {
          if (i >= myEnd || !StringUtil.isHexDigit(myBuffer.charAt(i))) return StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN;
        }
        return StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN;
      }
      else {
        return myOriginalLiteralToken; // b"\u1234" is just b"\\u1234", nothing gets escaped
      }
    }

    if (nextChar == 'x') { // \xNN is allowed both in bytes and unicode.
      for (int i = myStart + 2; i < myStart + 4; i++) {
        if (i >= myEnd || !StringUtil.isHexDigit(myBuffer.charAt(i))) return StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN;
      }
      return StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN;
    }

    if (nextChar == 'N' && isUnicodeMode()) {
      int i = myStart + 2;
      if (i >= myEnd || myBuffer.charAt(i) != '{') return StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN;
      i++;
      while (i < myEnd && myBuffer.charAt(i) != '}') i++;
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
      case '7':
        return StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN;
    }

    // other unrecognized escapes are just part of string, not an error
    return myOriginalLiteralToken;
  }

  protected boolean isEscape() {
    return myBuffer.charAt(myStart) == '\\' && (!isRaw() || isUnicodeMode() && nextIsUnicodeEscape());
  }

  private boolean nextIsUnicodeEscape() {
    if (myStart + 1 < myEnd) {
      char nextChar = myBuffer.charAt(myStart + 1);
      return nextChar == 'u' || nextChar == 'U';
    }
    return false;
  }

  // all subsequent chars are escaped spaces
  private boolean isTrailingSpace(final int start) {
    for (int i = start; i < myBufferEnd; i += 2) {
      final char c = myBuffer.charAt(i);
      if (c != '\\') return false;
      if (i == myBufferEnd - 1) return false;
      if (myBuffer.charAt(i + 1) != ' ') return false;
    }
    return true;
  }

  @Override
  public final int getTokenStart() {
    assert myStart < myEnd || (myStart == myEnd && myEnd == myBufferEnd);
    return myStart;
  }

  @Override
  public final int getTokenEnd() {
    if (!(myStart < myEnd || (myStart == myEnd && myEnd == myBufferEnd))) {
      LOG.error(
        "myStart=" + myStart + " myEnd=" + myEnd + " myBufferEnd=" + myBufferEnd + " text=" + myBuffer.subSequence(myStart, myBufferEnd));
    }
    return myEnd;
  }

  @Override
  public final int getBufferEnd() {
    return myBufferEnd;
  }

  @Override
  @NotNull
  public final CharSequence getBufferSequence() {
    return myBuffer;
  }

  protected abstract int locateToken(int start);

  protected final int locateEscapeSequence(int start) {
    assert myBuffer.charAt(start) == '\\';
    int i = start;
    i++;
    if (isRaw()) return i;
    if (i == myBufferEnd) {
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
        if (isEscapeEnd(i)) {
          return i;
        }
      }
      return i;
    }

    // unicode escape
    if (myBuffer.charAt(i) == 'u' || myBuffer.charAt(i) == 'U') {
      final int width = myBuffer.charAt(i) == 'u' ? 4 : 8; // is it uNNNN or Unnnnnnnn
      i++;
      for (; i < start + width + 2; i++) {
        if (isEscapeEnd(i)) {
          return i;
        }
      }
      return i;
    }

    if (myBuffer.charAt(i) == 'N' && isUnicodeMode()) {
      i++;
      while (i < myBufferEnd && myBuffer.charAt(i) != '}' && myBuffer.charAt(i) != '\\') {
        i++;
      }
      if (i < myBufferEnd && myBuffer.charAt(i) == '}') {
        i++;
      }
      return i;
    }
    return i + 1;
  }

  protected boolean isEscapeEnd(int offset) {
    return offset == myBufferEnd || myBuffer.charAt(offset) == '\n' || myBuffer.charAt(offset) == '\\';
  }

  @Override
  public void advance() {
    myStart = myEnd;
    myEnd = locateToken(myStart);
    if (!(myStart < myEnd || (myStart == myEnd && myEnd == myBufferEnd))) {
      LOG.warn("Inconsistent: start " + myStart + ", end " + myEnd + ", buf end " + myBufferEnd);
    }
    //assert myStart < myEnd || (myStart == myEnd && myEnd == myBufferEnd) : "Inconsistent: start " + myStart + ", end " + myEnd + ", buf end " + myBufferEnd;
  }
}
