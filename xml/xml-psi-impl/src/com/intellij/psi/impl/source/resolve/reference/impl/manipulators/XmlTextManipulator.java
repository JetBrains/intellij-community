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

package com.intellij.psi.impl.source.resolve.reference.impl.manipulators;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.IncorrectOperationException;
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
    if (Comparing.equal(value, newValue)) return text;
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