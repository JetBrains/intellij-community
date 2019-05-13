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
package com.intellij.psi.xml;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;

/**
 * @author mike
 */
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
