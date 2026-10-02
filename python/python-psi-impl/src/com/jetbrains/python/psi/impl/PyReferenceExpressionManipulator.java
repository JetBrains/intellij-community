// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class PyReferenceExpressionManipulator extends AbstractElementManipulator<PyReferenceExpression> {
  @Override
  public PyReferenceExpression handleContentChange(final @NotNull PyReferenceExpression element,
                                                   final @NotNull TextRange range,
                                                   final String newContent)
    throws IncorrectOperationException {
    return null;
  }

  @Override
  public @NotNull TextRange getRangeInElement(final @NotNull PyReferenceExpression element) {
    final ASTNode nameElement = element.getNameElement();
    final int startOffset = nameElement != null ? nameElement.getStartOffset() : element.getTextRange().getEndOffset();
    return new TextRange(startOffset - element.getTextOffset(), element.getTextLength());
  }
}
