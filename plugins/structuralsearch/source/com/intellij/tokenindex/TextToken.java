package com.intellij.tokenindex;

/**
 * @author Eugene.Kudelevsky
 */
public class TextToken extends Token {
  private final int myHash;

  public TextToken(int hash, int start, int end) {
    super(start, end);
    myHash = hash;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TextToken textToken = (TextToken)o;

    if (myHash != textToken.myHash) return false;

    return true;
  }

  @Override
  public String toString() {
    return Integer.toString(myHash);
  }

  @Override
  public int hashCode() {
    return myHash;
  }

  public int getHash() {
    return myHash;
  }
}
