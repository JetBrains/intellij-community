/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.text;

import java.util.Enumeration;
import java.util.NoSuchElementException;

/**
 * Copy of java.util.StringTokenizer with getCurrentPosition method.
 */
public class StringTokenizer implements Enumeration {
  private int currentPosition;
  private int newPosition;
  private int maxPosition;
  private String str;
  private String delimiters;
  private boolean retDelims;
  private boolean delimsChanged;

  /**
   * maxDelimChar stores the value of the delimiter character with the
   * highest value. It is used to optimize the detection of delimiter
   * characters.
   */
  private char maxDelimChar;

  /**
   * Set maxDelimChar to the highest char in the delimiter set.
   */
  private void setMaxDelimChar() {
    if (delimiters == null) {
      maxDelimChar = 0;
      return;
    }

    char m = 0;
    for (int i = 0; i < delimiters.length(); i++) {
      char c = delimiters.charAt(i);
      if (m < c)
        m = c;
    }
    maxDelimChar = m;
  }

  public StringTokenizer(String str, String delim, boolean returnDelims) {
    currentPosition = 0;
    newPosition = -1;
    delimsChanged = false;
    this.str = str;
    maxPosition = str.length();
    delimiters = delim;
    retDelims = returnDelims;
    setMaxDelimChar();
  }

  public StringTokenizer(String str, String delim) {
    this(str, delim, false);
  }

  public StringTokenizer(String str) {
    this(str, " \t\n\r\f", false);
  }

  private int skipDelimiters(int startPos) {
    if (delimiters == null)
      throw new NullPointerException();

    int position = startPos;
    while (!retDelims && position < maxPosition) {
      char c = str.charAt(position);
      if ((c > maxDelimChar) || (delimiters.indexOf(c) < 0))
        break;
      position++;
    }
    return position;
  }

  private int scanToken(int startPos) {
    int position = startPos;
    while (position < maxPosition) {
      char c = str.charAt(position);
      if ((c <= maxDelimChar) && (delimiters.indexOf(c) >= 0))
        break;
      position++;
    }
    if (retDelims && (startPos == position)) {
      char c = str.charAt(position);
      if ((c <= maxDelimChar) && (delimiters.indexOf(c) >= 0))
        position++;
    }
    return position;
  }

  public boolean hasMoreTokens() {
    newPosition = skipDelimiters(currentPosition);
    return (newPosition < maxPosition);
  }

  public String nextToken() {
    currentPosition = (newPosition >= 0 && !delimsChanged) ?
        newPosition : skipDelimiters(currentPosition);

    /* Reset these anyway */
    delimsChanged = false;
    newPosition = -1;

    if (currentPosition >= maxPosition)
      throw new NoSuchElementException();
    int start = currentPosition;
    currentPosition = scanToken(currentPosition);
    return str.substring(start, currentPosition);
  }

  public String nextToken(String delim) {
    delimiters = delim;

    /* delimiter string specified, so set the appropriate flag. */
    delimsChanged = true;

    setMaxDelimChar();
    return nextToken();
  }

  public boolean hasMoreElements() {
    return hasMoreTokens();
  }

  public Object nextElement() {
    return nextToken();
  }

  public int countTokens() {
    int count = 0;
    int currpos = currentPosition;
    while (currpos < maxPosition) {
      currpos = skipDelimiters(currpos);
      if (currpos >= maxPosition)
        break;
      currpos = scanToken(currpos);
      count++;
    }
    return count;
  }

  public int getCurrentPosition() {
    return currentPosition;
  }
}
