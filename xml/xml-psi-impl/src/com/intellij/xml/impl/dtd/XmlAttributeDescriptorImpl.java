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
package com.intellij.xml.impl.dtd;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.meta.PsiWritableMetaData;
import com.intellij.psi.xml.XmlAttributeDecl;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.impl.BasicXmlAttributeDescriptor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Mike
 */
public class XmlAttributeDescriptorImpl extends BasicXmlAttributeDescriptor implements PsiWritableMetaData {
  private XmlAttributeDecl myDecl;
  private boolean myRequired;
  private boolean myEnumerated;
  private boolean myFixed;
  private String myName;

  public XmlAttributeDescriptorImpl() {

  }
  public XmlAttributeDescriptorImpl(XmlAttributeDecl decl) {
    init(decl);
  }

  public boolean isRequired() {
    return myRequired;
  }

  public PsiElement getDeclaration(){
    return myDecl;
  }

  public String getName() {
    if (myName!=null) {
      return myName;
    }
    myName = myDecl.getNameElement().getText();
    return myName;
  }

  public void init(PsiElement element){
    myDecl = (XmlAttributeDecl) element;
    myRequired = myDecl.isAttributeRequired();
    myFixed = myDecl.isAttributeFixed();
    myEnumerated = myDecl.isEnumerated();
  }

  public Object[] getDependences(){
    return new Object[]{myDecl};
  }

  public boolean isFixed() {
    return myFixed;
  }

  public boolean hasIdType() {
    return myDecl.isIdAttribute();
  }

  public boolean hasIdRefType() {
    return myDecl.isIdRefAttribute();
  }

  public String getDefaultValue() {
    String text = myDecl.getDefaultValueText();
    if (text != null) {
      return text.substring(1, text.length() - 1);
    }

    return null;
  }

  public boolean isEnumerated() {
    return myEnumerated;
  }

  public String[] getEnumeratedValues() {

    XmlElement[] values = myDecl.getEnumeratedValues();
    List<String> result = new ArrayList<String>();
    for (XmlElement value : values) {
      result.add(value.getText());
    }

    return ArrayUtil.toStringArray(result);
  }

  public String getQualifiedName() {
    return getName();
  }

  public void setName(String name) throws IncorrectOperationException {
    myName = name;
    ((PsiNamedElement)getDeclaration()).setName(name);
  }
}
