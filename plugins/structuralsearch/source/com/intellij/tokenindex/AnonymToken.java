package com.intellij.tokenindex;

/**
 * @author Eugene.Kudelevsky
 */
public class AnonymToken extends Token {
  private final byte myType;

  public AnonymToken(byte type, int start, int end) {
    super(start, end);
    myType = type;
  }

  public byte getType() {
    return myType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AnonymToken that = (AnonymToken)o;

    if (myType != that.myType) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myType;
  }
}
