package com.intellij.tokenindex;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class Token {
  private final int myOffset;

  public Token(int offset) {
    myOffset = offset;
  }

  public int getOffset() {
    return myOffset;
  }
}
