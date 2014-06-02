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

/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Sep 30, 2002
 * Time: 8:55:08 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.xml.impl.schema;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;
import com.intellij.xml.XmlNSDescriptor;

public  class AnyXmlElementDescriptor implements XmlElementDescriptor {
  private final XmlElementDescriptor myParentDescriptor;
  private final XmlNSDescriptor myXmlNSDescriptor;

  public AnyXmlElementDescriptor(XmlElementDescriptor parentDescriptor, XmlNSDescriptor xmlNSDescriptor) {
    myParentDescriptor = parentDescriptor == null ? NullElementDescriptor.getInstance() : parentDescriptor;
    myXmlNSDescriptor = xmlNSDescriptor;
  }

  @Override
  public XmlNSDescriptor getNSDescriptor() {
    return myXmlNSDescriptor;
  }

  @Override
  public XmlElementsGroup getTopGroup() {
    return null;
  }

  @Override
  public PsiElement getDeclaration(){
    return null;
  }

  @Override
  public String getName(PsiElement context){
    return getName();
  }

  @Override
  public String getName() {
    return myParentDescriptor.getName();
  }

  @Override
  public void init(PsiElement element){
  }

  @Override
  public Object[] getDependences(){
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public String getQualifiedName() {
    return myParentDescriptor.getQualifiedName();
  }

  @Override
  public String getDefaultName() {
    return myParentDescriptor.getDefaultName();
  }

  @Override
  public XmlElementDescriptor[] getElementsDescriptors(XmlTag context) {
    return myParentDescriptor.getElementsDescriptors(context);
  }

  @Override
  public XmlElementDescriptor getElementDescriptor(XmlTag tag, XmlTag contextTag){
    return new AnyXmlElementDescriptor(this, myXmlNSDescriptor);
  }

  @Override
  public XmlAttributeDescriptor[] getAttributesDescriptors(final XmlTag context) {
    return new XmlAttributeDescriptor[0];
  }

  @Override
  public XmlAttributeDescriptor getAttributeDescriptor(final String attributeName, final XmlTag context) {
    return new AnyXmlAttributeDescriptor(attributeName);
  }

  @Override
  public XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attr){
    return myParentDescriptor.getAttributeDescriptor(attr);
  }

  @Override
  public int getContentType() {
    return CONTENT_TYPE_UNKNOWN;
  }

  @Override
  public String getDefaultValue() {
    return null;
  }
}
