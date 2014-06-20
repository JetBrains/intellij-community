package com.intellij.tokenindex;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class Token {
  private final int myStart;
  private final int myEnd;

  public Token(int start, int end) {
    myStart = start;
    myEnd = end;
  }

  public int getStart() {
    return myStart;
  }

  public int getEnd() {
    return myEnd;
  }
}
