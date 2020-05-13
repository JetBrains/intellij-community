// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.xml;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;

public interface XmlEntityDecl extends XmlElement, PsiNamedElement {
  enum EntityContextType {
    ELEMENT_CONTENT_SPEC, ATTRIBUTE_SPEC, ATTLIST_SPEC, ENTITY_DECL_CONTENT, GENERIC_XML,
    ENUMERATED_TYPE, ATTR_VALUE
  }

  @Override
  String getName();
  PsiElement getNameElement();
  XmlAttributeValue getValueElement();
  PsiElement parse(PsiFile baseFile, EntityContextType context, XmlEntityRef originalElement);
  boolean isInternalReference();
}
