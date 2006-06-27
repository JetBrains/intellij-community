/*
 * Copyright 2000-2006 JetBrains s.r.o.
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

package com.intellij.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.source.resolve.reference.ElementManipulator;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Dmitry Avdeev
 */
public abstract class PsiReferenceBase<T extends PsiElement> implements PsiReference {

  protected final T myElement;

  public PsiReferenceBase(T element) {
    myElement = element;
  }

  public T getElement() {
    return myElement;
  }

  public TextRange getRangeInElement() {
    return new TextRange(0, myElement.getTextLength());
  }

  public String getCanonicalText() {
    return StringUtil.stripQuotesAroundValue(myElement.getText());
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final ElementManipulator<T> manipulator = ReferenceProvidersRegistry.getInstance(myElement.getProject()).getManipulator(myElement);
    assert manipulator != null;
    return manipulator.handleContentChange(myElement, getRangeInElement(), newElementName);
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException("Rebind cannot be performed");
  }

  public boolean isReferenceTo(PsiElement element) {
    return element.getManager().areElementsEquivalent(element, resolve());
  }

  public boolean isSoft() {
    return false;
  }
}
