package com.intellij.codeInsight.template.emmet.tokens;

import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class IdentifierToken extends ZenCodingToken {
  private final String myText;

  public IdentifierToken(@NotNull String text) {
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
