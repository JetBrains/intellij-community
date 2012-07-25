package com.jetbrains.rest.validation;

import com.intellij.psi.PsiElement;
import com.jetbrains.rest.RestBundle;
import com.jetbrains.rest.psi.RestInlineBlock;

/**
 * Looks for invalid inline block
 *
 * User : catherine
 */
public class RestInlineBlockAnnotator extends RestAnnotator {

  @Override
  public void visitInlineBlock(final RestInlineBlock node) {
    if (!node.isValid()) {
      PsiElement el = node.getLastChild();
      if (el != null) {
        if (el.getText().equals("\n") && el.getPrevSibling() != null)
          el = el.getPrevSibling();
        markError(el, RestBundle.message("ANN.inline.block"));
      }
    }
  }
}
