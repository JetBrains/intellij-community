package org.jetbrains.plugins.textmate.language.syntax;

import org.jetbrains.annotations.NotNull;

/**
 * Syntax rule that represents include-rule of another rule by its name
 * <p/>
 * User: zolotov
 */
public final class SyntaxRuleProxyDescriptor extends SyntaxProxyDescriptor {
  private final int myRuleId;

  SyntaxRuleProxyDescriptor(int ruleId, @NotNull SyntaxNodeDescriptor parentNode) {
    super(parentNode);
    myRuleId = ruleId;
  }

  @Override
  protected SyntaxNodeDescriptor computeTargetNode() {
    return getParentNode().findInRepository(myRuleId);
  }

  @Override
  public String toString() {
    return "Proxy rule for " + myRuleId;
  }
}
