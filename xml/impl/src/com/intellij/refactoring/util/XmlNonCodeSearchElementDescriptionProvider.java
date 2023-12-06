// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util;

import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;


public class XmlNonCodeSearchElementDescriptionProvider implements ElementDescriptionProvider {
  @Override
  public String getElementDescription(final @NotNull PsiElement element, final @NotNull ElementDescriptionLocation location) {
    if (!(location instanceof NonCodeSearchDescriptionLocation ncdLocation)) return null;
    if (ncdLocation.isNonJava()) return null;
    if (element instanceof XmlTag) {
      return ((XmlTag)element).getValue().getTrimmedText();
    }
    else if (element instanceof XmlAttribute) {
      return ((XmlAttribute)element).getValue();
    }
    else if (element instanceof XmlAttributeValue) {
      return ((XmlAttributeValue)element).getValue();
    }
    return null;
  }
}
