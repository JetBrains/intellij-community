package com.intellij.ide.highlighter.custom.tokens;


/**
 * @author dsl
 */
public abstract class BaseTokenParser implements TokenParser {
  protected char[] myBuffer;
  protected int myStartOffset;
  protected int myEndOffset;
  protected final TokenInfo myTokenInfo = new TokenInfo();

  public void setBuffer(char[] buffer, int startOffset, int endOffset) {
    myBuffer = buffer;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
  }

  public void getTokenInfo(TokenInfo info) {
    info.updateData(myTokenInfo);
  }
}
