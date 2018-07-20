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

import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.ElementManipulator;
import com.intellij.psi.xml.XmlTag;
import com.intellij.openapi.util.TextRange;
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
    final ElementManipulator<XmlTag> manipulator = ElementManipulators.getManipulator(myTag);
    manipulator.handleContentChange(myTag, myRange, str);
    myRange = manipulator.getRangeInElement(myTag);
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    return myTag.getManager().areElementsEquivalent(element, resolve());
  }

  @Override
  public boolean isSoft() {
    return false;
  }
}
