// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.psi;

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
