package org.jetbrains.plugins.textmate.language.syntax;

import org.jetbrains.annotations.NotNull;

/**
 * Syntax rule that represents include-rule of another rule by its name
 * <p/>
 * User: zolotov
 */
public class SyntaxRuleProxyDescriptor extends SyntaxProxyDescriptor {
  @NotNull 
  private final String myRuleName;

  SyntaxRuleProxyDescriptor(@NotNull String ruleName, @NotNull SyntaxNodeDescriptor parentNode) {
    super(parentNode);
    myRuleName = ruleName;
  }

  @Override
  protected SyntaxNodeDescriptor computeTargetNode() {
    return getParentNode().findInRepository(myRuleName);
  }
  
  
  @Override
  public String toString() {
    return "Proxy rule for '" + myRuleName + "'";
  }
}
