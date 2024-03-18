// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.python.reStructuredText.validation.RestElementVisitor;
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
  public boolean validBlock() {
    String text = getText();
    if (text.endsWith("\n")) {
      text = text.substring(0, text.length()-1);
      final int index = text.lastIndexOf("\n");
      if (index > -1 && StringUtil.isEmptyOrSpaces(text.substring(index)))
        return true;
    }
    return false;
  }

  @Override
  protected void acceptRestVisitor(RestElementVisitor visitor) {
    visitor.visitInlineBlock(this);
  }
}
