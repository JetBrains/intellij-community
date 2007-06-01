/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.psi.impl.source.resolve.reference.impl.manipulators;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Gregory.Shrago
 */
public class PsiDocTagValueManipulator extends AbstractElementManipulator<PsiDocTag> {

  public PsiDocTag handleContentChange(PsiDocTag tag, TextRange range, String newContent) throws IncorrectOperationException {
    final StringBuilder replacement = new StringBuilder( tag.getText() );

    replacement.replace(
      range.getStartOffset(),
      range.getEndOffset(),
      newContent
    );
    return (PsiDocTag)tag.replace(tag.getManager().getElementFactory().createDocTagFromText(replacement.toString(), null));
  }

  public TextRange getRangeInElement(final PsiDocTag tag) {
    final PsiElement[] elements = tag.getDataElements();
    if (elements.length == 0) {
      final PsiElement name = tag.getNameElement();
      final int offset = name.getStartOffsetInParent() + name.getTextLength();
      return new TextRange(offset, offset);
    }
    final PsiElement first = elements[0];
    final PsiElement last = elements[elements.length - 1];
    return new TextRange(first.getStartOffsetInParent(), last.getStartOffsetInParent()+last.getTextLength());
  }
}