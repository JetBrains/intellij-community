/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.html.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.*;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;

public class DelegatingRelaxedHtmlElementDescriptor implements XmlElementDescriptor, XmlElementDescriptorAwareAboutChildren {
  protected final XmlElementDescriptor myDelegate;

  public DelegatingRelaxedHtmlElementDescriptor(@NotNull XmlElementDescriptor delegate) {myDelegate = delegate;}

  @Override
  public XmlElementDescriptor getElementDescriptor(XmlTag childTag, XmlTag contextTag) {
    XmlElementDescriptor elementDescriptor = myDelegate.getElementDescriptor(childTag, contextTag);

    if (elementDescriptor == null) {
      return RelaxedHtmlFromSchemaElementDescriptor.getRelaxedDescriptor(this, childTag);
    }

    return elementDescriptor;
  }

  @Override
  public String getQualifiedName() {
    return myDelegate.getQualifiedName();
  }

  @Override
  public String getDefaultName() {
    return myDelegate.getDefaultName();
  }

  @Override
  public XmlElementDescriptor[] getElementsDescriptors(final XmlTag context) {
    return ArrayUtil.mergeArrays(
      myDelegate.getElementsDescriptors(context),
      HtmlUtil.getCustomTagDescriptors(context)
    );
  }

  @Override
  public XmlAttributeDescriptor[] getAttributesDescriptors(final XmlTag context) {
    return RelaxedHtmlFromSchemaElementDescriptor.addAttrDescriptorsForFacelets(context, myDelegate.getAttributesDescriptors(context));
  }

  @Override
  public XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attribute) {
    XmlAttributeDescriptor descriptor = myDelegate.getAttributeDescriptor(attribute);
    if (descriptor != null) return descriptor;

    return getAttributeDescriptor(attribute.getName(), attribute.getParent());
  }

  @Override
  public XmlNSDescriptor getNSDescriptor() {
    return myDelegate.getNSDescriptor();
  }

  @Override
  public XmlElementsGroup getTopGroup() {
    return myDelegate.getTopGroup();
  }

  @Override
  public int getContentType() {
    return myDelegate.getContentType();
  }

  @Override
  public String getDefaultValue() {
    return null;
  }

  @Override
  public XmlAttributeDescriptor getAttributeDescriptor(String attributeName, final XmlTag context) {
    final XmlAttributeDescriptor descriptor = myDelegate.getAttributeDescriptor(attributeName.toLowerCase(), context);
    if (descriptor != null) return descriptor;

    return RelaxedHtmlFromSchemaElementDescriptor.getAttributeDescriptorFromFacelets(attributeName, context);
  }

  @Override
  public PsiElement getDeclaration() {
    return myDelegate.getDeclaration();
  }

  @Override
  public String getName(PsiElement context) {
    return myDelegate.getName(context);
  }

  @Override
  public String getName() {
    return myDelegate.getName();
  }

  @Override
  public void init(PsiElement element) {
    myDelegate.init(element);
  }

  @NotNull
  @Override
  public Object[] getDependences() {
    return myDelegate.getDependences();
  }

  @Override
  public boolean allowElementsFromNamespace(String namespace, XmlTag context) {
    return true;
  }

  @Override
  public int hashCode() {
    return myDelegate.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this ||
           (obj instanceof DelegatingRelaxedHtmlElementDescriptor
            && myDelegate.equals(((DelegatingRelaxedHtmlElementDescriptor)obj).myDelegate));
  }
}
