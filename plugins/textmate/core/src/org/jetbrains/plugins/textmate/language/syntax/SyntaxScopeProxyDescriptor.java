package org.jetbrains.plugins.textmate.language.syntax;

import org.jetbrains.annotations.NotNull;

/**
 * Syntax rule that represents include-rule for rule from syntax scope of another language.
 * Empty rule value means including entire syntax scope.
 * <p/>
 * User: zolotov
 */
public final class SyntaxScopeProxyDescriptor extends SyntaxProxyDescriptor {
  private final @NotNull CharSequence myScope;
  private final @NotNull TextMateSyntaxTableCore mySyntaxTable;
  private final int myRuleId;

  SyntaxScopeProxyDescriptor(@NotNull CharSequence scope,
                             int ruleId,
                             @NotNull TextMateSyntaxTableCore syntaxTable,
                             @NotNull SyntaxNodeDescriptor parentNode) {
    super(parentNode);
    myScope = scope;
    myRuleId = ruleId;
    mySyntaxTable = syntaxTable;
  }

  @Override
  protected SyntaxNodeDescriptor computeTargetNode() {
    SyntaxNodeDescriptor parentNode = mySyntaxTable.getSyntax(myScope);
    return myRuleId > -1 ? parentNode.findInRepository(myRuleId) : parentNode;
  }

  @Override
  public String toString() {
    return "Proxy rule for '" + myScope + "[" + myRuleId + "]' scope";
  }
}
