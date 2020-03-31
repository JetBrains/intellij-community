// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
*/
public abstract class XmlValueReference implements PsiReference {
  protected final XmlTag myTag;
  protected TextRange myRange;

  protected XmlValueReference(XmlTag tag) {
    myTag = tag;
    myRange = ElementManipulators.getValueTextRange(tag);
  }

  @NotNull
  @Override
  public PsiElement getElement() {
    return myTag;
  }

  @NotNull
  @Override
  public TextRange getRangeInElement() {
    return myRange;
  }

  @Override
  @NotNull
  public String getCanonicalText() {
    return myRange.substring(myTag.getText());
  }

  protected void replaceContent(final String str) throws IncorrectOperationException {
    ElementManipulators.handleContentChange(myTag, myRange, str);
    myRange = ElementManipulators.getValueTextRange(myTag);
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    return myTag.getManager().areElementsEquivalent(element, resolve());
  }

  @Override
  public boolean isSoft() {
    return false;
  }
}
