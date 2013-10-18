package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.PyReferenceExpression;

/**
 * @author oleg
 */
public class PyReferenceExpressionManipulator extends AbstractElementManipulator<PyReferenceExpression> {
  public PyReferenceExpression handleContentChange(final PyReferenceExpression element, final TextRange range, final String newContent)
    throws IncorrectOperationException {
    return null;
  }

  @Override
  public TextRange getRangeInElement(final PyReferenceExpression element) {
    final ASTNode nameElement = element.getNameElement();
    final int startOffset = nameElement != null ? nameElement.getStartOffset() : element.getTextRange().getEndOffset();
    return new TextRange(startOffset - element.getTextOffset(), element.getTextLength());
  }
}
