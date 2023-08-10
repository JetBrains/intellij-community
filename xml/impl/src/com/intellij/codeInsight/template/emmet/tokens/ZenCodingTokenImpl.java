package com.intellij.codeInsight.template.emmet.tokens;

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
