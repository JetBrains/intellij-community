/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.text;

public class CharArrayCharSequence implements CharSequence {
  private final char[] myChars;
  private final int myStart;
  private final int myEnd;

  public CharArrayCharSequence(char[] chars) {
    this(chars, 0, chars.length);
  }

  public CharArrayCharSequence(char[] chars, int start, int end) {
    myChars = chars;
    myStart = start;
    myEnd = end;
  }

  public int length() {
    return myEnd - myStart;
  }

  public char charAt(int index) {
    return myChars[index + myStart];
  }

  public CharSequence subSequence(int start, int end) {
    return new CharArrayCharSequence(myChars, myStart + start, myStart + end);
  }

  public String toString() {
    return new String(myChars, myStart, myEnd - myStart); //TODO StringFactory
  }

  public char[] getChars() {
    if (myStart == 0 /*&& myEnd == myChars.length*/) return myChars;
    char[] chars = new char[length()];
    System.arraycopy(myChars, myStart, chars, 0, length());
    return chars;
  }

  public void getChars(char[] dst, int dstOffset) {
    System.arraycopy(myChars, myStart, dst, dstOffset, length());
  }
}
