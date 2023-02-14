package com.intellij.codeInsight.template.emmet.tokens;

public class StringLiteralToken extends ZenCodingToken {
  private final String myText;

  public StringLiteralToken(String text) {
    myText = text;
  }

  @Override
  public String toString() {
    return myText;
  }

  public String getText() {
    return myText;
  }
}
