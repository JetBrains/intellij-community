// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.xml.impl.schema;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.ArrayUtilRt;
import com.intellij.xml.XmlAttributeDescriptor;

public class AnyXmlAttributeDescriptor implements XmlAttributeDescriptor {
  private final String myAttributeName;
  private final ComplexTypeDescriptor.CanContainAttributeType myCanContainAttributeType;

  public AnyXmlAttributeDescriptor(String attributeName) {
    this(attributeName, ComplexTypeDescriptor.CanContainAttributeType.CanContainButDoNotSkip);
  }

  public AnyXmlAttributeDescriptor(String attributeName, ComplexTypeDescriptor.CanContainAttributeType canContainAttributeType) {
    myAttributeName = attributeName;
    myCanContainAttributeType = canContainAttributeType;
  }

  @Override
  public PsiElement getDeclaration(){
    return null;
  }

  @Override
  public String getName(PsiElement context){
    return myAttributeName;
  }

  @Override
  public String getName() {
    return myAttributeName;
  }

  @Override
  public void init(PsiElement element){
  }

  public String getQualifiedName() {
    return myAttributeName;
  }

  public String getDefaultName() {
    return myAttributeName;
  }

  @Override
  public boolean isRequired() {
    return false;
  }

  @Override
  public boolean isFixed() {
    return false;
  }

  @Override
  public boolean hasIdType() {
    return false;
  }

  @Override
  public boolean hasIdRefType() {
    return false;
  }

  @Override
  public String getDefaultValue() {
    return null;
  }

  //todo: refactor to hierarchy of value descriptor?
  @Override
  public boolean isEnumerated() {
    return false;
  }

  @Override
  public String[] getEnumeratedValues() {
    return ArrayUtilRt.EMPTY_STRING_ARRAY;
  }

  @Override
  public String validateValue(XmlElement context, String value) {
    return null;
  }

  public ComplexTypeDescriptor.CanContainAttributeType getCanContainAttributeType() {
    return myCanContainAttributeType;
  }
}
