package com.intellij.tokenindex;

/**
 * @author Eugene.Kudelevsky
 */
public class IndentToken extends Token {
  public IndentToken(int start, int end) {
    super(start, end);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof IndentToken;
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
