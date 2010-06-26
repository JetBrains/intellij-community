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
package com.intellij.util.xml;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.PomRenameableTarget;
import com.intellij.pom.PsiDeclaredTarget;
import com.intellij.psi.DelegatePsiTarget;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class DomTarget extends DelegatePsiTarget implements PsiDeclaredTarget, PomRenameableTarget {
  private final DomElement myDomElement;
  private final TextRange myRange;
  private GenericDomValue myNameDomElement;

  private DomTarget(DomElement domElement, PsiElement navigationElement, TextRange range, final GenericDomValue nameElement) {
    super(navigationElement);
    myDomElement = domElement;
    myRange = range;
    myNameDomElement = nameElement;
  }

  @Nullable
  public static DomTarget getTarget(@NotNull DomElement element) {
    final GenericDomValue nameElement = element.getGenericInfo().getNameDomElement(element);
    if (nameElement == null) {
      return null;
    }

    return getTarget(element, nameElement);
  }

  @Nullable
  public static DomTarget getTarget(DomElement element, GenericDomValue nameElement) {
    if (nameElement instanceof GenericAttributeValue) {
      final GenericAttributeValue genericAttributeValue = (GenericAttributeValue)nameElement;
      final XmlAttributeValue attributeValue = genericAttributeValue.getXmlAttributeValue();
      if (attributeValue == null) {
        return null;
      }

      final int length = attributeValue.getTextLength();
      if (length > 2) {
        return new DomTarget(element, attributeValue, new TextRange(1, length - 1), nameElement);
      }
    }

    final XmlTag tag = nameElement.getXmlTag();
    if (tag == null) {
      return null;
    }

    XmlTagValue tagValue = tag.getValue();
    if (StringUtil.isEmpty(tagValue.getTrimmedText())) {
      return null;
    }

    return new DomTarget(element, tag, XmlTagUtil.getTrimmedValueRange(tag), nameElement);
  }

  public TextRange getNameIdentifierRange() {
    return myRange;
  }

  public boolean isWritable() {
    return getNavigationElement().isWritable();
  }

  public Object setName(@NotNull String newName) {
    myNameDomElement.setStringValue(newName);
    return myDomElement;
  }

  @Nullable
  public String getName() {
    return myNameDomElement.getStringValue();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    DomTarget domTarget = (DomTarget)o;

    if (myDomElement != null ? !myDomElement.equals(domTarget.myDomElement) : domTarget.myDomElement != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (myDomElement != null ? myDomElement.hashCode() : 0);
    result = 31 * result + (myRange != null ? myRange.hashCode() : 0);
    return result;
  }

  public DomElement getDomElement() {
    return myDomElement;
  }
}
