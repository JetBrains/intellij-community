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
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public abstract class BasicAttributeValueReference implements PsiReference {
  protected final PsiElement myElement;
  protected final TextRange myRange;

  public BasicAttributeValueReference(final PsiElement element) {
    this ( element, ElementManipulators.getValueTextRange(element));
  }

  public BasicAttributeValueReference(final PsiElement element, int offset) {
    this (element, createTextRange(element, offset));
  }

  @NotNull
  private static TextRange createTextRange(PsiElement element, int offset) {
    int valueEndOffset = element.getTextLength() - offset;
    // in case of not closed quote
    return new TextRange(offset, Math.max(offset, valueEndOffset));
  }

  public BasicAttributeValueReference(final PsiElement element, TextRange range) {
    myElement = element;
    myRange = range;
  }

  @NotNull
  public PsiElement getElement() {
    return myElement;
  }

  @NotNull
  public TextRange getRangeInElement() {
    return myRange;
  }

  @NotNull
  public String getCanonicalText() {
    final String s = myElement.getText();
    if (myRange.getStartOffset() < s.length() && myRange.getEndOffset() <= s.length()) {
      return myRange.substring(s);
    }
    return "";
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return ElementManipulators.getManipulator(myElement).handleContentChange(
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
