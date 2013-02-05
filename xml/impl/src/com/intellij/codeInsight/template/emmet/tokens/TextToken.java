package com.intellij.codeInsight.template.emmet.tokens;

/**
 * @author Eugene.Kudelevsky
 */
public class TextToken extends ZenCodingToken {
  private final String myText;

  public TextToken(String text) {
    myText = text;
  }

  public String getText() {
    return myText;
  }

  @Override
  public String toString() {
    return myText;
  }
}
