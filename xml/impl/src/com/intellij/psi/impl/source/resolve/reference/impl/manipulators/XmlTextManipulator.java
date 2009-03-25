/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
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