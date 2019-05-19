/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.xml.impl.schema;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;
import com.intellij.xml.XmlNSDescriptor;

/**
 * @author cdr
 */
public class NullElementDescriptor implements XmlElementDescriptor {
  private static final NullElementDescriptor INSTANCE = new NullElementDescriptor();

  public static NullElementDescriptor getInstance() {
    return INSTANCE;
  }

  private NullElementDescriptor() {
  }

  @Override
  public String getQualifiedName() {
    return null;
  }

  @Override
  public String getDefaultName() {
    return null;
  }

  //todo: refactor to support full DTD spec
  @Override
  public XmlElementDescriptor[] getElementsDescriptors(XmlTag context) {
    return XmlElementDescriptor.EMPTY_ARRAY;
  }

  @Override
  public XmlElementDescriptor getElementDescriptor(XmlTag childTag, XmlTag contextTag) {
    return null;
  }

  @Override
  public XmlAttributeDescriptor[] getAttributesDescriptors(final XmlTag context) {
    return XmlAttributeDescriptor.EMPTY;
  }

  @Override
  public XmlAttributeDescriptor getAttributeDescriptor(String attributeName, final XmlTag context) {
    return null;
  }

  @Override
  public XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attribute) {
    return null;
  }

  @Override
  public XmlNSDescriptor getNSDescriptor() {
    return null;
  }

  @Override
  public XmlElementsGroup getTopGroup() {
    return null;
  }

  @Override
  public int getContentType() {
    return 0;
  }

  @Override
  public String getDefaultValue() {
    return null;
  }

  @Override
  public PsiElement getDeclaration() {
    return null;
  }

  @Override
  public String getName(PsiElement context) {
    return null;
  }

  @Override
  public String getName() {
    return null;
  }

  @Override
  public void init(PsiElement element) {
  }
}
