/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public abstract class BasicAttributeValueReference implements PsiReference {
  protected PsiElement myElement;
  protected TextRange myRange;

  public BasicAttributeValueReference(final PsiElement element) {
    this ( element, 1);
  }

  public BasicAttributeValueReference(final PsiElement element, int offset) {
    this ( element, new TextRange(offset, element.getTextLength() - offset));
  }

  public BasicAttributeValueReference(final PsiElement element, TextRange range) {
    myElement = element;
    myRange = range;
  }

  public PsiElement getElement() {
    return myElement;
  }

  public TextRange getRangeInElement() {
    return myRange;
  }

  public String getCanonicalText() {
    final String s = myElement.getText();
    if (myRange.getStartOffset() < s.length() && myRange.getEndOffset() <= s.length()) {
      return s.substring(myRange.getStartOffset(),myRange.getEndOffset());
    }
    return "";
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return ReferenceProvidersRegistry.getInstance(myElement.getProject()).getManipulator(myElement).handleContentChange(
      myElement,
      getRangeInElement(),
      newElementName
    );
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  public boolean isReferenceTo(PsiElement element) {
    return myElement.getManager().areElementsEquivalent(element, resolve());
  }
}
