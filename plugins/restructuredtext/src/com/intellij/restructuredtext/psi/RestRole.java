// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.restructuredtext.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.restructuredtext.validation.RestElementVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 */
public class RestRole extends RestElement {
  public RestRole(final @NotNull ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "RestRole:" + getNode().getElementType().toString();
  }

  public @NlsSafe String getRoleName() {
    String text = getNode().getText();
    return text.substring(1, text.length()-1);
  }

  @Override
  protected void acceptRestVisitor(RestElementVisitor visitor) {
    visitor.visitRole(this);
  }
}
