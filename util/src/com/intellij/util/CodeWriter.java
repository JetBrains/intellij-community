/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util;

import java.io.PrintWriter;
import java.util.StringTokenizer;

/**
 * @author dsl
 */
public class CodeWriter extends PrintWriter {
  private final int myIndent;
  private int myIndentLevel = 0;

  // Printer state
  private boolean myNewLineStarted = true;

  public CodeWriter(PrintWriter writer) {
    super(writer);
    myIndent = 2;
  }

  public void print(String s) {
    possiblyIndent(s);
    super.print(s);
    for (int i = 0; i < s.length(); i++) {
      if (isOpenBrace(s, i)) myIndentLevel++;
      if (isCloseBrace(s, i)) myIndentLevel--;
    }
  }


  private static boolean isCloseBrace(String s, int index) {
    char c = s.charAt(index);

    return c == ')' || c == ']' || c == '}';
  }

  private static boolean isOpenBrace(String s, int index) {
    char c = s.charAt(index);

    return c == '(' || c == '[' || c == '{';
  }

  public void println() {
    ((PrintWriter)out).println();
    myNewLineStarted = true;
  }

  private void possiblyIndent(String s) {
    if (myNewLineStarted) {
      int i = 0;
      for (; i < s.length() && s.charAt(i) == ' '; i++) {
      }
      int firstNonBlank = (i < s.length() && s.charAt(i) != ' ') ? i : -1;
      if (firstNonBlank >= 0) {
        if (isCloseBrace(s, firstNonBlank)) myIndentLevel--;
        int blanksToPrint = myIndent * myIndentLevel - firstNonBlank;
        for (int j = 0; j < blanksToPrint; j++) {
          write(" ");
        }
        if (isCloseBrace(s, firstNonBlank)) myIndentLevel++;
      }
      myNewLineStarted = false;
    }
  }

  public void println(String s) {
    StringTokenizer st = new StringTokenizer(s, "\r\n", false);

    while (st.hasMoreTokens()) {
      super.println(st.nextToken());
    }
  }

}
