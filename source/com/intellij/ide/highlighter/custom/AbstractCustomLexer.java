package com.intellij.ide.highlighter.custom;

import com.intellij.psi.CustomHighlighterTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.ide.highlighter.custom.tokens.TokenInfo;
import com.intellij.ide.highlighter.custom.tokens.TokenParser;
import com.intellij.lexer.Lexer;

/**
 * @author dsl
 */
public abstract class AbstractCustomLexer implements Lexer {
  protected char[] myBuffer = new char[0];
  protected int myStartOffset = 0;
  protected int myEndOffset = 0;
  private static final short START_STATE = (short) 0;
  private final TokenParser[] myTokenParsers;
  private final int mySmartUpdateShift;
  private TokenInfo myCurrentToken;
  private int myPosition;

  public AbstractCustomLexer(TokenParser[] tokenParsers) {
    myTokenParsers = tokenParsers;

    int smartUpdateShift = 0;
    for (int i = 0; i < myTokenParsers.length; i++) {
      TokenParser tokenParser = myTokenParsers[i];
      smartUpdateShift = Math.max(smartUpdateShift, tokenParser.getSmartUpdateShift());
    }
    mySmartUpdateShift = smartUpdateShift;
  }

  public void start(char[] buffer) {
    start(buffer, 0, buffer.length, START_STATE);
  }

  public void start(char[] buffer, int startOffset, int endOffset) {
    start(buffer, startOffset, endOffset, START_STATE);
  }

  public void start(char[] buffer, int startOffset, int endOffset, int initialState) {
    myBuffer = buffer;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myPosition = myStartOffset;
    myCurrentToken = new TokenInfo();
    for (int i = 0; i < myTokenParsers.length; i++) {
      TokenParser tokenParser = myTokenParsers[i];
      tokenParser.setBuffer(myBuffer, myStartOffset, myEndOffset);
    }
    advance();
  }

  public int getState() {
    return 0;
  }

  public int getLastState() {
    return 0;
  }

  public IElementType getTokenType() {
    return myCurrentToken.getType();
  }

  public int getTokenStart() {
    return myCurrentToken.getStart();
  }

  public int getTokenEnd() {
    return myCurrentToken.getEnd();
  }

  public void advance() {
    if (myPosition >= myEndOffset) {
      myCurrentToken.updateData(myPosition, myPosition, null);
      return;
    }
    boolean tokenFound = false;
    for (int i = 0; i < myTokenParsers.length; i++) {
      TokenParser tokenParser = myTokenParsers[i];
      if (tokenParser.hasToken(myPosition)) {
        tokenParser.getTokenInfo(myCurrentToken);
        tokenFound = true;
        break;
      }
    }

    if (!tokenFound) {
      myCurrentToken.updateData(myPosition, myPosition + 1, CustomHighlighterTokenType.CHARACTER);
    }
    myPosition = myCurrentToken.getEnd();
  }

  public char[] getBuffer() {
    return myBuffer;
  }

  public int getBufferEnd() {
    return myEndOffset;
  }

  public int getSmartUpdateShift() {
    return mySmartUpdateShift;
  }

  public abstract Object clone();
}
