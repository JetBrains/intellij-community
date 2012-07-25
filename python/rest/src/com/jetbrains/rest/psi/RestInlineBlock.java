package com.jetbrains.rest.psi;

import com.intellij.lang.ASTNode;
import com.jetbrains.rest.validation.RestElementVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 */
public class RestInlineBlock extends RestElement {
  public RestInlineBlock(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "RestInlineBlock";
  }
  public boolean isValid() {
    return getText().matches("(.|\n)*\n *\n");
  }

  @Override
  protected void acceptRestVisitor(RestElementVisitor visitor) {
    visitor.visitInlineBlock(this);
  }
}
