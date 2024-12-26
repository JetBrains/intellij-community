// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties;

import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author gregsh
 */
public class PropertyManipulator extends AbstractElementManipulator<PropertyImpl> {
  @Override
  public PropertyImpl handleContentChange(@NotNull PropertyImpl element, @NotNull TextRange range, String newContent) throws IncorrectOperationException {
    TextRange valueRange = getRangeInElement(element);
    final String oldText = element.getText();
    String newText = oldText.substring(0, range.getStartOffset()) + newContent + oldText.substring(range.getEndOffset());
    element.setValue(newText.substring(valueRange.getStartOffset()).replaceAll("([^\\s])\n", "$1 \n"));  // add explicit space before \n
    return element;
  }

  @Override
  public @NotNull TextRange getRangeInElement(@NotNull PropertyImpl element) {
    ASTNode valueNode = element.getValueNode();
    if (valueNode == null) return TextRange.from(element.getTextLength(), 0);
    TextRange range = valueNode.getTextRange();
    return TextRange.from(range.getStartOffset() - element.getTextRange().getStartOffset(), range.getLength());
  }
}
