/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util.text;

import com.intellij.util.ArrayUtil;
import com.intellij.util.text.CharArrayCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class LineTokenizer {
  private int myOffset = 0;
  private int myLength = 0;
  private int myLineSeparatorLength = 0;
  private boolean atEnd = false;
  private CharSequence myText;

  public static String[] tokenize(CharSequence chars, boolean includeSeparators) {
    return tokenize(chars, includeSeparators, true);
  }

  private static String[] tokenize(final CharSequence chars, final boolean includeSeparators, final boolean skipLastEmptyLine) {
    if (chars == null || chars.length() == 0){
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    LineTokenizer tokenizer = new LineTokenizer(chars);
    List<String> lines = new ArrayList<String>();
    while(!tokenizer.atEnd()){
      int offset = tokenizer.getOffset();
      String line;
      if (includeSeparators){
        line = chars.subSequence(offset, offset + tokenizer.getLength() + tokenizer.getLineSeparatorLength()).toString();
      }
      else{
        line = chars.subSequence(offset, offset + tokenizer.getLength()).toString();
      }
      lines.add(line);
      tokenizer.advance();
    }

    if (!skipLastEmptyLine && stringEdnsWithSeparator(tokenizer)) lines.add("");

    return lines.toArray(new String[lines.size()]);
  }

  public static String[] tokenize(char[] chars, boolean includeSeparators) {
    return tokenize(chars, includeSeparators, true);
  }

  public static String[] tokenize(char[] chars, boolean includeSeparators, boolean skipLastEmptyLine) {
    return tokenize(chars, 0, chars.length, includeSeparators, skipLastEmptyLine);
  }

  public static String[] tokenize(char[] chars, int startOffset, int endOffset, boolean includeSeparators,
                                  boolean skipLastEmptyLine) {
    return tokenize(new CharArrayCharSequence(chars, startOffset, endOffset), includeSeparators, skipLastEmptyLine);
  }

  private static boolean stringEdnsWithSeparator(LineTokenizer tokenizer) {
    return tokenizer.getLineSeparatorLength() > 0;
  }

  public static String[] tokenize(char[] chars, int startOffset, int endOffset, boolean includeSeparators) {
    return tokenize(chars, startOffset, endOffset, includeSeparators, true);
  }

  public LineTokenizer(CharSequence text) {
    myText = text;
    myOffset = 0;
    advance();
  }

  public LineTokenizer(char[] text, int startOffset, int endOffset) {
    this(new CharArrayCharSequence(text, startOffset, endOffset));
  }

  public final boolean atEnd() {
    return atEnd;
  }

  public final int getOffset() {
    return myOffset;
  }

  public final int getLength() {
    return myLength;
  }

  public final int getLineSeparatorLength() {
    return myLineSeparatorLength;
  }

  public void advance() {
    int i = myOffset + myLength + myLineSeparatorLength;
    if (i >= myText.length()){
      atEnd = true;
      return;
    }
    while(i < myText.length()){
      char c = myText.charAt(i);
      if (c == '\r' || c == '\n') break;
      i++;
    }

    myOffset = myOffset + myLength + myLineSeparatorLength;
    myLength = i - myOffset;

    myLineSeparatorLength = 0;
    if (i == myText.length())
      return;
    char first = myText.charAt(i);
    if (first == '\r' || first == '\n')
      myLineSeparatorLength = 1;
    i++;
    if (i == myText.length())
      return;
    char second = myText.charAt(i);
    if (first == '\r' && second == '\n')
      myLineSeparatorLength = 2;
  }
}
