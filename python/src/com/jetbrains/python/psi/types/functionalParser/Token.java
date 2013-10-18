package com.jetbrains.python.psi.types.functionalParser;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

/**
* @author vlan
*/
public class Token<T> {
  @NotNull private final CharSequence myText;
  @NotNull private final TextRange myRange;
  @NotNull private final T myType;

  public Token(@NotNull T type, @NotNull CharSequence text, @NotNull TextRange range) {
    myText = text;
    myRange = range;
    myType = type;
  }

  @NotNull
  public T getType() {
    return myType;
  }

  @NotNull
  public CharSequence getText() {
    return myText;
  }

  @NotNull
  public TextRange getRange() {
    return myRange;
  }

  @Override
  public String toString() {
    return String.format("Token(<%s>, \"%s\", %s)", myType, myText, myRange);
  }
}
