package com.jetbrains.rest.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
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
