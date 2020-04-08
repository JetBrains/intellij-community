// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.source.resolve.reference.impl.manipulators;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.IncorrectOperationException;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * @author Gregory.Shrago
 */
public class XmlTextManipulator extends AbstractElementManipulator<XmlText> {

  @Override
  public XmlText handleContentChange(@NotNull XmlText text, @NotNull TextRange range, String newContent) throws IncorrectOperationException {
    final String newValue;
    final String value = text.getValue();
    if (range.equals(getRangeInElement(text))) {
      newValue = newContent;
    }
    else {
      final StringBuilder replacement = new StringBuilder(value);
      replacement.replace(
        range.getStartOffset(),
        range.getEndOffset(),
        newContent
      );
      newValue = replacement.toString();
    }
    if (Objects.equals(value, newValue)) return text;
    if (!newValue.isEmpty()) {
      text.setValue(newValue);
    }
    else {
      text.deleteChildRange(text.getFirstChild(), text.getLastChild());
    }
    //String oldText = text.getText();
    //((PsiLanguageInjectionHost)text).updateText(
    //  oldText.substring(0, range.getStartOffset()) + newContent + oldText.substring(range.getEndOffset()));
    return text;
  }

  @Override
  @NotNull
  public TextRange getRangeInElement(@NotNull final XmlText text) {
    return getValueRange(text);
  }

  private static TextRange getValueRange(final XmlText xmlText) {
    final String value = xmlText.getValue();
    final int i = value.indexOf(value);
    final int start = xmlText.displayToPhysical(i);
    return value.isEmpty() ? new TextRange(start, start) : new TextRange(start, xmlText.displayToPhysical(i + value.length() - 1) + 1);
  }
}