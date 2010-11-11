package com.jetbrains.python.testing.doctest;

import java.util.StringTokenizer;

/**
 * User: catherine
 *
 * Lazy python doc string parser
 * Searches for occurence of '>>>' in docstring
 */
public class PythonDocStringParser {
  private Boolean hasExample;

  PythonDocStringParser(String docString) {
    hasExample = false;
    StringTokenizer tokenizer = new StringTokenizer(docString, "\n");
    while (tokenizer.hasMoreTokens()) {
      String str = tokenizer.nextToken().trim();
      if (str.startsWith(">>>")) {
        hasExample = true;
        break;
      }
    }
  }

  Boolean hasExample() {
      return hasExample;
  }
}
