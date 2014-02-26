/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  @NotNull
  @Override
  public TextRange getRangeInElement(@NotNull PropertyImpl element) {
    ASTNode valueNode = element.getValueNode();
    if (valueNode == null) return TextRange.from(element.getTextLength(), 0);
    TextRange range = valueNode.getTextRange();
    return TextRange.from(range.getStartOffset() - element.getTextRange().getStartOffset(), range.getLength());
  }
}
