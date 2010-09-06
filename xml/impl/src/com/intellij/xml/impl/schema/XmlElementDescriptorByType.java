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
package com.intellij.xml.impl.schema;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;

/**
 * @author ik
 */
public class XmlElementDescriptorByType extends XmlElementDescriptorImpl {
  private ComplexTypeDescriptor myType;
  @NonNls
  public static final String QUALIFIED_ATTR_VALUE = "qualified";

  public XmlElementDescriptorByType(XmlTag instanceTag, ComplexTypeDescriptor descriptor) {
    myDescriptorTag = instanceTag;
    myType = descriptor;
  }

  public XmlElementDescriptorByType() {}

  public PsiElement getDeclaration(){
    return myDescriptorTag;
  }

  public String getName(PsiElement context){
    return myDescriptorTag.getName();
  }

  public XmlNSDescriptor getNSDescriptor() {
    XmlNSDescriptor nsDescriptor = NSDescriptor;
    if (nsDescriptor ==null) {
      final XmlFile file = XmlUtil.getContainingFile(getType(null).getDeclaration());
      if(file == null) return null;
      final XmlDocument document = file.getDocument();
      if(document == null) return null;
      NSDescriptor = nsDescriptor = (XmlNSDescriptor)document.getMetaData();
    }

    return nsDescriptor;
  }

  /**
   * @return minimal occurrence constraint value (e.g. 0 or 1), on null if not applied
   */
  @Override
  public Integer getMinOccurs() {
    return null;
  }

  /**
   * @return maximal occurrence constraint value (e.g. 1 or {@link Integer.MAX_VALUE}), on null if not applied
   */
  @Override
  public Integer getMaxOccurs() {
    return null;
  }

  public ComplexTypeDescriptor getType(XmlElement context) {
    return myType;
  }

  public String getDefaultName() {
    XmlTag rootTag = ((XmlFile)getType(null).getDeclaration().getContainingFile()).getDocument().getRootTag();

    if (QUALIFIED_ATTR_VALUE.equals(rootTag.getAttributeValue("elementFormDefault"))) {
      return getQualifiedName();
    }

    return getName();
  }
  
  protected boolean askParentDescriptorViaXsi() {
    return false;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof XmlElementDescriptorByType)) return false;

    final XmlElementDescriptorByType that = (XmlElementDescriptorByType)o;

    if (myType != null ? !myType.equals(that.myType) : that.myType != null) return false;

    return true;
  }

  public int hashCode() {
    return (myType != null ? myType.hashCode() : 0);
  }
}
