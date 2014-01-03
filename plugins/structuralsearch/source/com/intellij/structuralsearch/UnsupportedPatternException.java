package com.intellij.structuralsearch;

/**
 * Exception about encountering yet unsupported pattern event.
 */
public class UnsupportedPatternException extends RuntimeException {

  public UnsupportedPatternException(String _pattern) {
    super(_pattern);
  }
}
