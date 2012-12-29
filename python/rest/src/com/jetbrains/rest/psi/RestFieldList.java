package com.jetbrains.rest.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * User : ktisha
 */
public class RestFieldList extends RestElement {
  public RestFieldList(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "RestFieldList:" + getNode().getElementType().toString();
  }

}
