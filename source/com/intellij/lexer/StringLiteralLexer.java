package com.intellij.lexer;

import com.intellij.ide.highlighter.custom.tokens.HexNumberParser;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.tree.IElementType;

/**
 * @author max
 */
public class StringLiteralLexer implements Lexer, Cloneable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lexer.StringLiteralLexer");

  private static final short BEFORE_FIRST_QUOTE = 0;
  private static final short AFTER_FIRST_QUOTE = 1;
  private static final short AFTER_LAST_QUOTE = 2;
  private static final short LAST_STATE = AFTER_LAST_QUOTE;

  private char[] myBuffer;
  private int myStart;
  private int myEnd;
  private int myState;
  private int myLastState;
  private int myBufferEnd;
  private char myQuoteChar;

  public StringLiteralLexer(char quoteChar) {
    myQuoteChar = quoteChar;
  }

  public void start(char[] buffer) {
    start(buffer, 0, buffer.length);
  }

  public void start(char[] buffer, int startOffset, int endOffset, int initialState) {
    myBuffer = buffer;
    myStart = startOffset;
    myState = initialState;
    myLastState = initialState;
    myBufferEnd = endOffset;
    myEnd = locateToken(myStart);
  }

  public void start(char[] buffer, int startOffset, int endOffset) {
    start(buffer, startOffset, endOffset, BEFORE_FIRST_QUOTE);
  }

  public int getState() {
    return myLastState;
  }

  public int getLastState() {
    return LAST_STATE;
  }

  public IElementType getTokenType() {
    if (myStart >= myEnd) return null;

    if (myBuffer[myStart] != '\\') return JavaTokenType.STRING_LITERAL;

    if (myStart + 1 >= myEnd) return StringEscapesTokenTypes.INVALID_STRING_ESCAPE_TOKEN;
    if (myBuffer[myStart + 1] == 'u') {
      for(int i = myStart + 2; i < myStart + 6; i++) {
        if (i >= myEnd || !HexNumberParser.isHexDigit(myBuffer[i])) return StringEscapesTokenTypes.INVALID_STRING_ESCAPE_TOKEN;
      }
      return StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN;
    }

    switch (myBuffer[myStart + 1]) {
      case 'n':
      case 'r':
      case 'b':
      case 't':
      case 'f':
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

    return StringEscapesTokenTypes.INVALID_STRING_ESCAPE_TOKEN;
  }

  public int getTokenStart() {
    return myStart;
  }

  public int getTokenEnd() {
    return myEnd;
  }

  private int locateToken(int start) {
    if (start == myBufferEnd) {
      myState = AFTER_LAST_QUOTE;
    }
    if (myState == AFTER_LAST_QUOTE) return start;
    int i = start;
    if (myBuffer[i] == '\\') {
      LOG.assertTrue(myState == AFTER_FIRST_QUOTE);
      i++;
      if (i == myBufferEnd || myBuffer[i] == '\n') {
        myState = AFTER_LAST_QUOTE;
        return i;
      }

      if (myBuffer[i] >= '0' && myBuffer[i] <= '7') {
        char first = myBuffer[i];
        i++;
        if (i < myBufferEnd && myBuffer[i] >= '0' && myBuffer[i] <= '7') {
          i++;
          if (i < myBufferEnd && first <= '3' && myBuffer[i] >= '0' && myBuffer[i] <= '7') {
            i++;
          }
        }
        return i;
      }

      if (myBuffer[i] == 'u') {
        i++;
        for (; i < start + 6; i++) {
          if (i == myBufferEnd ||
              myBuffer[i] == '\n' ||
              myBuffer[i] == myQuoteChar) {
            return i;
          }
        }
        return i;
      } else {
        return i + 1;
      }
    } else {
      LOG.assertTrue(myState == AFTER_FIRST_QUOTE || myBuffer[i] == myQuoteChar);
      while (i < myBufferEnd) {
        if (myBuffer[i] == '\\') {
          return i;
        }
        if (myBuffer[i] == '\n') {
          myState = AFTER_LAST_QUOTE;
          return i;
        }
        if (myState == AFTER_FIRST_QUOTE && myBuffer[i] == myQuoteChar) {
          myState = AFTER_LAST_QUOTE;
          return i + 1;
        }
        i++;
        myState = AFTER_FIRST_QUOTE;
      }
    }

    return i;
  }

  public void advance() {
    myLastState = myState;
    myStart = myEnd;
    myEnd = locateToken(myStart);
  }

  public char[] getBuffer() {
    return myBuffer;
  }

  public int getBufferEnd() {
    return myBufferEnd;
  }

  public int getSmartUpdateShift() {
    return 6;
  }

  public Object clone() {
    try {
      return super.clone();
    } catch (CloneNotSupportedException e) {
      LOG.error(e);
    }
    return null;
  }
}
