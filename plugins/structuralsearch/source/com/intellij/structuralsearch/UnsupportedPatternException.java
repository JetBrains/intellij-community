package com.intellij.structuralsearch;

/**
 * Exception about encountering yet unsupported pattern event.
 */
public class UnsupportedPatternException extends RuntimeException {
  private String pattern;

  public UnsupportedPatternException(String _pattern) {
    pattern = _pattern;
  }

  public String toString() {
    return pattern;
  }
}
