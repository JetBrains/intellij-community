// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.validation;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.python.reStructuredText.RestBundle;
import com.intellij.python.reStructuredText.psi.RestInlineBlock;

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
        getHolder().newAnnotation(HighlightSeverity.ERROR, RestBundle.message("ANN.inline.block")).range(TextRange.create(endOffset - 1, endOffset)).create();
      }
    }
  }
}
