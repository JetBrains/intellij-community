package com.jetbrains.rest.validation;

import com.intellij.openapi.util.TextRange;
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
    if (!node.validBlock()) {
      PsiElement el = node.getLastChild();
      if (el != null) {
        final int endOffset = node.getTextRange().getEndOffset();
        getHolder().createErrorAnnotation(TextRange.create(endOffset-1, endOffset), RestBundle.message("ANN.inline.block"));
      }
    }
  }
}
