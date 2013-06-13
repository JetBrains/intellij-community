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

/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Sep 30, 2002
 * Time: 9:46:52 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.xml.impl.schema;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.ArrayUtil;
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

  public PsiElement getDeclaration(){
    return null;
  }

  public String getName(PsiElement context){
    return myAttributeName;
  }

  public String getName() {
    return myAttributeName;
  }

  public void init(PsiElement element){
  }

  public Object[] getDependences(){
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public String getQualifiedName() {
    return myAttributeName;
  }

  public String getDefaultName() {
    return myAttributeName;
  }

  public boolean isRequired() {
    return false;
  }

  public boolean isFixed() {
    return false;
  }

  public boolean hasIdType() {
    return false;
  }

  public boolean hasIdRefType() {
    return false;
  }

  public String getDefaultValue() {
    return null;
  }

  //todo: refactor to hierarchy of value descriptor?
  public boolean isEnumerated() {
    return false;
  }

  public String[] getEnumeratedValues() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public String validateValue(XmlElement context, String value) {
    return null;
  }

  public ComplexTypeDescriptor.CanContainAttributeType getCanContainAttributeType() {
    return myCanContainAttributeType;
  }
}
