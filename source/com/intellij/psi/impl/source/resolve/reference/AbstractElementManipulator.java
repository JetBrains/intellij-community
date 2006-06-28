/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Dmitry Avdeev
 */
public abstract class AbstractElementManipulator<T extends PsiElement> implements ElementManipulator<T> {

  public T handleContentChange(final T element, final String newContent) throws IncorrectOperationException {
    return handleContentChange(element, getRangeInElement(element), newContent);
  }

  public TextRange getRangeInElement(final T element) {
    return new TextRange(0, element.getTextLength());
  }
}
