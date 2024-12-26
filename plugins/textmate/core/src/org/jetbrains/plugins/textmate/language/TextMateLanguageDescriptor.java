package org.jetbrains.plugins.textmate.language;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.language.syntax.SyntaxNodeDescriptor;

public final class TextMateLanguageDescriptor {
  private final @NotNull CharSequence scopeName;
  private final @NotNull SyntaxNodeDescriptor rootSyntaxNode;

  public TextMateLanguageDescriptor(@NotNull CharSequence name, @NotNull SyntaxNodeDescriptor node) {
    scopeName = name;
    rootSyntaxNode = node;
  }

  public @NotNull SyntaxNodeDescriptor getRootSyntaxNode() {
    return rootSyntaxNode;
  }

  public @NotNull CharSequence getScopeName() {
    return scopeName;
  }
}
