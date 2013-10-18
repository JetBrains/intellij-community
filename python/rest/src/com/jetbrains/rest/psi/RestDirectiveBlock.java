package com.jetbrains.rest.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 */
public class RestDirectiveBlock extends RestElement {
  public RestDirectiveBlock(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "RestDirective:" + getNode().getElementType().toString();
  }

  @NotNull
  public String getDirectiveName() {
    PsiElement child = this.getFirstChild();
    if (child != null)
      return child.getText();
    else
      return "";
  }
}
