/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.xml.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author mike
 */
public class XmlIncludeHandler {
  @NonNls private static final String INCLUDE_TAG_NAME = "include";
  public static boolean isXInclude(PsiElement element) {
    if (element instanceof XmlTag) {
      XmlTag xmlTag = (XmlTag)element;

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
      Collections.sort(references, new Comparator<PsiReference>() {
        @Override
        public int compare(PsiReference reference1, PsiReference reference2) {
          return reference2.getRangeInElement().getStartOffset() - reference1.getRangeInElement().getStartOffset();
        }
      });
      PsiElement target = references.get(0).resolve();
      if (target instanceof XmlFile) {
        return (XmlFile) target;
      }
    }
    return null;
  }
}
