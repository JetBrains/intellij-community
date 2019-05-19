/*
 * Copyright 2005 Sascha Weinreuter
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

package org.intellij.lang.xpath.xslt.impl.references;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class SelfReference implements PsiReference {
  private final XmlAttributeValue myValue;
  private final PsiElement myTarget;
  private final int myStartOffset;

  SelfReference(XmlAttribute element, PsiElement target, int startOffset) {
    myTarget = target;
    myValue = element.getValueElement();
    myStartOffset = startOffset;
  }

  SelfReference(XmlAttribute element, PsiElement target) {
    this(element, target, 0);
  }

  @Override
  @NotNull
  public PsiElement getElement() {
    return myValue;
  }

  @Override
  @NotNull
  public TextRange getRangeInElement() {
    return TextRange.from(1 + myStartOffset, myValue.getTextLength() - (2 + myStartOffset));
  }

  @Override
  @Nullable
  public PsiElement resolve() {
    return myValue.isValid() ? myTarget : null;
  }

  @Override
  @NotNull
  public String getCanonicalText() {
    return myValue.getText();
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    return myValue;
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return myValue;
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    return false;
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  public static SelfReference create(XmlAttribute element, PsiElement target) {
    if (element.getValue().contains(":")) {
      return new SelfReference(element, target, element.getValue().indexOf(':') + 1);
    }
    return new SelfReference(element, target);
  }
}
