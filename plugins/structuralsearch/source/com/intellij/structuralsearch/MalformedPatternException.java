package com.intellij.structuralsearch;

/**
 * Class to indicate incorrect pattern
 */
public class MalformedPatternException extends RuntimeException {
  public MalformedPatternException() {}
  public MalformedPatternException(String msg) { super(msg); }
}
