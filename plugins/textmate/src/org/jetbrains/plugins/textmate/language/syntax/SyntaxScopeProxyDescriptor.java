package org.jetbrains.plugins.textmate.language.syntax;

import org.jetbrains.annotations.NotNull;

/**
 * Syntax rule that represents include-rule of entire syntax scope (e.g. including another language)
 * <p/>
 * User: zolotov
 */
public class SyntaxScopeProxyDescriptor extends SyntaxProxyDescriptor {
  @NotNull private final CharSequence myScope;
  @NotNull private final TextMateSyntaxTable mySyntaxTable;

  SyntaxScopeProxyDescriptor(@NotNull CharSequence scope, @NotNull TextMateSyntaxTable syntaxTable, @NotNull SyntaxNodeDescriptor parentNode) {
    super(parentNode);
    myScope = scope;
    mySyntaxTable = syntaxTable;
  }

  @Override
  protected SyntaxNodeDescriptor computeTargetNode() {
    return mySyntaxTable.getSyntax(myScope);
  }

  @Override
  public String toString() {
    return "Proxy rule for '" + myScope + "' scope";
  }
}
