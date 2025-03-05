package org.jetbrains.plugins.textmate.language.syntax.lexer;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.TextMateLanguage;

public class TextMateElementType extends IElementType {
  private final TextMateScope myScope;

  public TextMateElementType(@NotNull TextMateScope scope) {
    super("TEXTMATE_TOKEN", TextMateLanguage.LANGUAGE, false);
    myScope = scope;
  }

  public @NotNull TextMateScope getScope() {
    return myScope;
  }

  @Override
  public int hashCode() {
    return getScope().hashCode();
  }

  @Override
  public String toString() {
    return myScope.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    return ((TextMateElementType)o).getScope().equals(getScope());
  }
}
