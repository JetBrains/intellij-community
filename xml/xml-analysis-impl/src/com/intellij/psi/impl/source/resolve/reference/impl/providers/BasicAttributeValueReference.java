// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public abstract class BasicAttributeValueReference implements PsiReference {
  protected final PsiElement myElement;
  protected final TextRange myRange;

  public BasicAttributeValueReference(final PsiElement element) {
    this ( element, ElementManipulators.getValueTextRange(element));
  }

  public BasicAttributeValueReference(final PsiElement element, int offset) {
    this (element, createTextRange(element, offset));
  }

  private static @NotNull TextRange createTextRange(PsiElement element, int offset) {
    int valueEndOffset = element.getTextLength() - offset;
    // in case of not closed quote
    return new TextRange(offset, Math.max(offset, valueEndOffset));
  }

  public BasicAttributeValueReference(final PsiElement element, TextRange range) {
    myElement = element;
    myRange = range;
  }

  @Override
  public @NotNull PsiElement getElement() {
    return myElement;
  }

  @Override
  public @NotNull TextRange getRangeInElement() {
    return myRange;
  }

  @Override
  public @NotNull String getCanonicalText() {
    final String s = myElement.getText();
    if (myRange.getStartOffset() < s.length() && myRange.getEndOffset() <= s.length()) {
      return myRange.substring(s);
    }
    return "";
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    return ElementManipulators.handleContentChange(
      myElement,
      getRangeInElement(),
      newElementName
    );
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    return myElement.getManager().areElementsEquivalent(element, resolve());
  }
}
