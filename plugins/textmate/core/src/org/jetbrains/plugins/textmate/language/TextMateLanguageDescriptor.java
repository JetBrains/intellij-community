package org.jetbrains.plugins.textmate.language;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.language.syntax.SyntaxNodeDescriptor;

public final class TextMateLanguageDescriptor {
  @NotNull private final CharSequence scopeName;
  @NotNull private final SyntaxNodeDescriptor rootSyntaxNode;

  public TextMateLanguageDescriptor(@NotNull CharSequence name, @NotNull SyntaxNodeDescriptor node) {
    scopeName = name;
    rootSyntaxNode = node;
  }

  @NotNull
  public SyntaxNodeDescriptor getRootSyntaxNode() {
    return rootSyntaxNode;
  }

  @NotNull
  public CharSequence getScopeName() {
    return scopeName;
  }
}
