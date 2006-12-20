package com.intellij.util.text;

public class CharSequenceSubSequence implements CharSequence {
  private final CharSequence myChars;
  private final int myStart;
  private final int myEnd;

  public CharSequenceSubSequence(CharSequence chars) {
    this(chars, 0, chars.length());
  }

  public CharSequenceSubSequence(CharSequence chars, int start, int end) {
    if (start < 0 || end > chars.length() || start > end) {
      throw new IndexOutOfBoundsException("chars sequence.length:" + chars.length() +
                                          ", start:" + start +
                                          ", end:" + end);
    }
    myChars = chars;
    myStart = start;
    myEnd = end;
  }

  public final int length() {
    return myEnd - myStart;
  }

  public final char charAt(int index) {
    return myChars.charAt(index + myStart);
  }

  public CharSequence subSequence(int start, int end) {
    if (start == myStart && end == myEnd) return this;
    return new CharSequenceSubSequence(myChars, myStart + start, myStart + end);
  }

  public String toString() {
    if (myChars instanceof String) return ((String)myChars).substring(myStart, myEnd);
    return new String(CharArrayUtil.fromSequence(myChars), myStart, myEnd - myStart); //TODO StringFactory
  }
}
