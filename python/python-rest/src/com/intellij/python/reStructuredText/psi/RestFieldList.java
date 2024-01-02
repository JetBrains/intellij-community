// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.psi;

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
