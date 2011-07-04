package com.intellij.codeInsight.template.zencoding.tokens;

/**
 * @author Eugene.Kudelevsky
 */
public class ZenCodingTokenImpl extends ZenCodingToken {
  private final String myString;

  public ZenCodingTokenImpl(String string) {
    myString = string;
  }

  @Override
  public String toString() {
    return myString;
  }
}
