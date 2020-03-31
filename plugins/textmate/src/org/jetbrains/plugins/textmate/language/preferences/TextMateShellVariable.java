package org.jetbrains.plugins.textmate.language.preferences;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.language.TextMateScopeSelectorOwner;

public class TextMateShellVariable implements TextMateScopeSelectorOwner {
  public final CharSequence scopeName;
  public final String name;
  public final String value;

  public TextMateShellVariable(@NotNull CharSequence scopeName, @NotNull String name, @NotNull String value) {
    this.scopeName = scopeName;
    this.name = name;
    this.value = value;
  }

  @NotNull
  @Override
  public CharSequence getScopeSelector() {
    return scopeName;
  }
}
