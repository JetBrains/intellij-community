// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public final class XmlIncludeHandler {
  @NonNls private static final String INCLUDE_TAG_NAME = "include";
  public static boolean isXInclude(PsiElement element) {
    if (element instanceof XmlTag xmlTag) {

      if (xmlTag.getParent() instanceof XmlDocument) return false;

      if (xmlTag.getLocalName().equals(INCLUDE_TAG_NAME) && xmlTag.getAttributeValue("href") != null) {
        if (xmlTag.getNamespace().equals(XmlPsiUtil.XINCLUDE_URI)) {
          return true;
        }
      }
    }

    return false;
  }

  @Nullable
  public static XmlFile resolveXIncludeFile(XmlTag xincludeTag) {
    final XmlAttribute hrefAttribute = xincludeTag.getAttribute("href", null);
    if (hrefAttribute == null) return null;

    final XmlAttributeValue xmlAttributeValue = hrefAttribute.getValueElement();
    if (xmlAttributeValue == null) return null;

    List<PsiReference> references = Arrays.asList(xmlAttributeValue.getReferences());
    if (references.size() > 0) {
      references.sort(
        (reference1, reference2) -> reference2.getRangeInElement().getStartOffset() - reference1.getRangeInElement().getStartOffset());
      PsiElement target = references.get(0).resolve();
      if (target instanceof XmlFile) {
        return (XmlFile) target;
      }
    }
    return null;
  }
}
