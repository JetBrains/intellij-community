/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util;

public class TextRange {
  private final int myStartOffset;
  private final int myEndOffset;

  public TextRange(int startOffset, int endOffset) {
    myStartOffset = startOffset;
    myEndOffset = endOffset;
  }

  public final int getStartOffset() {
    return myStartOffset;
  }

  public final int getEndOffset() {
    return myEndOffset;
  }

  public final int getLength() {
    return myEndOffset - myStartOffset;
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof TextRange)) return false;
    TextRange range = (TextRange)obj;
    return myStartOffset == range.myStartOffset && myEndOffset == range.myEndOffset;
  }

  public int hashCode() {
    return myStartOffset + myEndOffset;
  }

  public boolean contains(TextRange anotherRange) {
    return myStartOffset <= anotherRange.getStartOffset() && myEndOffset >= anotherRange.getEndOffset();
  }

  public String toString() {
    return "(" + myStartOffset + "," + myEndOffset + ")";
  }

  public boolean contains(int offset) {
    return myStartOffset <= offset && offset < myEndOffset;
  }

  public String substring(String str) {
    return str.substring(myStartOffset, myEndOffset);
  }

  public TextRange cutOut(TextRange subRange) {
    return new TextRange(myStartOffset + subRange.getStartOffset(), Math.min(myEndOffset, myStartOffset + subRange.getEndOffset()));
  }

  public TextRange shiftRight(int offset) {
    if (offset == 0) return this;
    return new TextRange(myStartOffset + offset, myEndOffset + offset);
  }

  public TextRange grown(int lengthDelta) {
    return from(myStartOffset, getLength() + lengthDelta);
  }

  public static TextRange from(int offset, int length) {
    return new TextRange(offset, offset + length);
  }

  public String replace(String original, String replacement) {
    String beggining = original.substring(0, getStartOffset());
    String ending = original.substring(getEndOffset(), original.length());
    return beggining + replacement + ending;
  }
}
