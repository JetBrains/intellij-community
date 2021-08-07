// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.impl.dtd;

import com.intellij.openapi.util.FieldCache;
import com.intellij.openapi.util.SimpleFieldCache;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;

import java.util.HashMap;

public abstract class BaseXmlElementDescriptorImpl implements XmlElementDescriptor {
  private volatile XmlElementDescriptor[] myElementDescriptors;
  private volatile XmlAttributeDescriptor[] myAttributeDescriptors;
  private volatile HashMap<String,XmlElementDescriptor> myElementDescriptorsMap;
  private volatile HashMap<String,XmlAttributeDescriptor> attributeDescriptorsMap;

  protected BaseXmlElementDescriptorImpl() {}

  @Override
  public XmlElementsGroup getTopGroup() {
    return null;
  }

  @Override
  public String getDefaultValue() {
    return null;
  }

  // Read-only action
  protected abstract XmlElementDescriptor[] doCollectXmlDescriptors(final XmlTag context);

  static final FieldCache<XmlElementDescriptor[],BaseXmlElementDescriptorImpl,Object, XmlTag> myElementDescriptorsCache =
    new FieldCache<>() {
      @Override
      protected XmlElementDescriptor[] compute(final BaseXmlElementDescriptorImpl xmlElementDescriptor, XmlTag tag) {
        return xmlElementDescriptor.doCollectXmlDescriptors(tag);
      }

      @Override
      protected XmlElementDescriptor[] getValue(final BaseXmlElementDescriptorImpl xmlElementDescriptor, Object o) {
        return xmlElementDescriptor.myElementDescriptors;
      }

      @Override
      protected void putValue(final XmlElementDescriptor[] xmlElementDescriptors,
                              final BaseXmlElementDescriptorImpl xmlElementDescriptor,
                              Object o) {
        xmlElementDescriptor.myElementDescriptors = xmlElementDescriptors;
      }
    };

  @Override
  public XmlElementDescriptor[] getElementsDescriptors(XmlTag context) {
    return myElementDescriptorsCache.get(null, this, context);
  }

  private static final SimpleFieldCache<XmlAttributeDescriptor[],BaseXmlElementDescriptorImpl> myAttributeDescriptorsCache =
    new SimpleFieldCache<>() {
      @Override
      protected XmlAttributeDescriptor[] compute(final BaseXmlElementDescriptorImpl xmlElementDescriptor) {
        return xmlElementDescriptor.collectAttributeDescriptors(null);
      }

      @Override
      protected XmlAttributeDescriptor[] getValue(final BaseXmlElementDescriptorImpl xmlElementDescriptor) {
        return xmlElementDescriptor.myAttributeDescriptors;
      }

      @Override
      protected void putValue(final XmlAttributeDescriptor[] xmlAttributeDescriptors,
                              final BaseXmlElementDescriptorImpl xmlElementDescriptor) {
        xmlElementDescriptor.myAttributeDescriptors = xmlAttributeDescriptors;
      }
    };

  @Override
  public XmlAttributeDescriptor[] getAttributesDescriptors(final XmlTag context) {
    return myAttributeDescriptorsCache.get(this);
  }

  // Read-only calculation
  protected abstract XmlAttributeDescriptor[] collectAttributeDescriptors(final XmlTag context);

  private static final SimpleFieldCache<HashMap<String,XmlAttributeDescriptor>, BaseXmlElementDescriptorImpl> attributeDescriptorsMapCache =
    new SimpleFieldCache<>() {
      @Override
      protected HashMap<String, XmlAttributeDescriptor> compute(final BaseXmlElementDescriptorImpl baseXmlElementDescriptor) {
        return baseXmlElementDescriptor.collectAttributeDescriptorsMap(null);
      }

      @Override
      protected HashMap<String, XmlAttributeDescriptor> getValue(final BaseXmlElementDescriptorImpl baseXmlElementDescriptor) {
        return baseXmlElementDescriptor.attributeDescriptorsMap;
      }

      @Override
      protected void putValue(final HashMap<String, XmlAttributeDescriptor> hashMap,
                              final BaseXmlElementDescriptorImpl baseXmlElementDescriptor) {
        baseXmlElementDescriptor.attributeDescriptorsMap = hashMap;
      }
    };

  @Override
  public XmlAttributeDescriptor getAttributeDescriptor(String attributeName, final XmlTag context) {
    return attributeDescriptorsMapCache.get(this).get(attributeName);
  }

  // Read-only calculation
  protected abstract HashMap<String, XmlAttributeDescriptor> collectAttributeDescriptorsMap(final XmlTag context);

  private static final FieldCache<HashMap<String,XmlElementDescriptor>,BaseXmlElementDescriptorImpl,Object,XmlTag> myElementDescriptorsMapCache =
    new FieldCache<>() {
      @Override
      protected HashMap<String, XmlElementDescriptor> compute(final BaseXmlElementDescriptorImpl baseXmlElementDescriptor,
                                                              final XmlTag p) {
        return baseXmlElementDescriptor.collectElementDescriptorsMap(p);
      }

      @Override
      protected HashMap<String, XmlElementDescriptor> getValue(final BaseXmlElementDescriptorImpl baseXmlElementDescriptor,
                                                               final Object p) {
        return baseXmlElementDescriptor.myElementDescriptorsMap;
      }

      @Override
      protected void putValue(final HashMap<String, XmlElementDescriptor> hashMap,
                              final BaseXmlElementDescriptorImpl baseXmlElementDescriptor, final Object p) {
        baseXmlElementDescriptor.myElementDescriptorsMap = hashMap;
      }
    };

  @Override
  public XmlElementDescriptor getElementDescriptor(XmlTag element, XmlTag contextTag) {
    return myElementDescriptorsMapCache.get(null, this, contextTag).get(element.getName());
  }

  public final XmlElementDescriptor getElementDescriptor(String name, XmlTag context){
    return myElementDescriptorsMapCache.get(null, this, context).get(name);
  }

  // Read-only calculation
  protected abstract HashMap<String, XmlElementDescriptor> collectElementDescriptorsMap(final XmlTag element);

  @Override
  public final XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attr){
    return getAttributeDescriptor(attr.getName(), attr.getParent());
  }

  @Override
  public String toString() {
    return getQualifiedName();
  }
}
