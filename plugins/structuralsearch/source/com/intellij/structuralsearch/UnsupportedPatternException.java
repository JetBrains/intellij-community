package com.intellij.structuralsearch;

import org.jetbrains.annotations.NonNls;

/**
 * Exception about encountering yet unsupported pattern event.
 */
public class UnsupportedPatternException extends RuntimeException {
  private String pattern;

  public UnsupportedPatternException(String _pattern) {
    pattern = _pattern;
  }

  public String getPattern() {
    return pattern;
  }
}
