package org.jetbrains.plugins.textmate.language.syntax;

import org.jetbrains.annotations.NotNull;

/**
 * Syntax rule that represents $self and $base include-rules.
 * <p/>
 * User: zolotov
 */
public final class SyntaxRootProxyDescriptor extends SyntaxProxyDescriptor {
  SyntaxRootProxyDescriptor(@NotNull SyntaxNodeDescriptor parentNode) {
    super(parentNode);
  }

  @Override
  protected SyntaxNodeDescriptor computeTargetNode() {
    SyntaxNodeDescriptor rootNode = getParentNode();
    SyntaxNodeDescriptor parentNode = getParentNode();
    while (parentNode != null) {
      rootNode = parentNode;
      parentNode = rootNode.getParentNode();
    }
    return rootNode;
  }
  
  @Override
  public String toString() {
    return "Proxy rule for root node";
  }
}