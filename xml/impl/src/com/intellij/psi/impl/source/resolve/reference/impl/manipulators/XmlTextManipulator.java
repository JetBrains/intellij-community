/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Gregory.Shrago
 */
public class XmlTextManipulator extends AbstractElementManipulator<XmlText> {

  public XmlText handleContentChange(XmlText text, TextRange range, String newContent) throws IncorrectOperationException {

    final StringBuilder replacement = new StringBuilder(text.getValue());
    replacement.replace(
      range.getStartOffset(),
      range.getEndOffset(),
      newContent
    );
    text.setValue(replacement.toString());
    return text;
  }

  public TextRange getRangeInElement(final XmlText text) {
    return TextRange.from(0, text.getTextLength());
  }
}