package org.jetbrains.plugins.textmate.language.syntax.lexer;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.language.TextMateLanguage;

public class TextMateElementType extends IElementType {
  public static final TextMateElementType EMPTY = new TextMateElementType("empty");

  public TextMateElementType(@NotNull @NonNls String debugName) {
    super(debugName, TextMateLanguage.LANGUAGE, false);
  }

  @NotNull
  public String getSelector() {
    return toString();
  }

  @Override
  public int hashCode() {
    return getSelector().hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    return ((TextMateElementType)o).getSelector().equals(getSelector());
  }
}
