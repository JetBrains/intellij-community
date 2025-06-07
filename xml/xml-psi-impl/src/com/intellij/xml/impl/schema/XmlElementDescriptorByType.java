// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.impl.schema;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;

public class XmlElementDescriptorByType extends XmlElementDescriptorImpl {
  private ComplexTypeDescriptor myType;
  public static final @NonNls String QUALIFIED_ATTR_VALUE = "qualified";

  public XmlElementDescriptorByType(XmlTag instanceTag, ComplexTypeDescriptor descriptor) {
    myDescriptorTag = instanceTag;
    myType = descriptor;
  }

  public XmlElementDescriptorByType() {}

  @Override
  public String getName(PsiElement context){
    return myDescriptorTag.getName();
  }

  @Override
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

  @Override
  public ComplexTypeDescriptor getType(XmlElement context) {
    return myType;
  }

  @Override
  public String getDefaultName() {
    XmlTag rootTag = ((XmlFile)getType(null).getDeclaration().getContainingFile()).getDocument().getRootTag();

    if (QUALIFIED_ATTR_VALUE.equals(rootTag.getAttributeValue("elementFormDefault"))) {
      return getQualifiedName();
    }

    return getName();
  }

  @Override
  protected boolean askParentDescriptorViaXsi() {
    return false;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof XmlElementDescriptorByType that)) return false;

    if (myType != null ? !myType.equals(that.myType) : that.myType != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return (myType != null ? myType.hashCode() : 0);
  }
}
