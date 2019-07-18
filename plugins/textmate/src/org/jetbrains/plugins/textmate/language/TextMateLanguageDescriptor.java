package org.jetbrains.plugins.textmate.language;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.language.syntax.SyntaxNodeDescriptor;

public class TextMateLanguageDescriptor {
  @NotNull private final String scopeName;
  @NotNull private final SyntaxNodeDescriptor rootSyntaxNode;

  public TextMateLanguageDescriptor(@NotNull String name, @NotNull SyntaxNodeDescriptor node) {
    scopeName = name;
    rootSyntaxNode = node;
  }

  @NotNull
  public SyntaxNodeDescriptor getRootSyntaxNode() {
    return rootSyntaxNode;
  }

  @NotNull
  public String getScopeName() {
    return scopeName;
  }
}
