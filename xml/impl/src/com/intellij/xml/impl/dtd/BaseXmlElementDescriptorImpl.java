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

import com.intellij.openapi.util.FieldCache;
import com.intellij.openapi.util.SimpleFieldCache;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;

import java.util.HashMap;

/**
 * @by maxim
 */
public abstract class BaseXmlElementDescriptorImpl implements XmlElementDescriptor {
  private volatile XmlElementDescriptor[] myElementDescriptors = null;
  private volatile XmlAttributeDescriptor[] myAttributeDescriptors;
  private volatile HashMap<String,XmlElementDescriptor> myElementDescriptorsMap;
  private volatile HashMap<String,XmlAttributeDescriptor> attributeDescriptorsMap;

  protected BaseXmlElementDescriptorImpl() {}

  @Override
  public XmlElementsGroup getTopGroup() {
    return null;
  }

  // Read-only action
  protected abstract XmlElementDescriptor[] doCollectXmlDescriptors(final XmlTag context);

  static final FieldCache<XmlElementDescriptor[],BaseXmlElementDescriptorImpl,Object, XmlTag> myElementDescriptorsCache =
    new FieldCache<XmlElementDescriptor[], BaseXmlElementDescriptorImpl, Object, XmlTag>() {
    protected final XmlElementDescriptor[] compute(final BaseXmlElementDescriptorImpl xmlElementDescriptor, XmlTag tag) {
      return xmlElementDescriptor.doCollectXmlDescriptors(tag);
    }

    protected final XmlElementDescriptor[] getValue(final BaseXmlElementDescriptorImpl xmlElementDescriptor, Object o) {
      return xmlElementDescriptor.myElementDescriptors;
    }

    protected final void putValue(final XmlElementDescriptor[] xmlElementDescriptors, final BaseXmlElementDescriptorImpl xmlElementDescriptor,Object o) {
      xmlElementDescriptor.myElementDescriptors = xmlElementDescriptors;
    }
  };

  public XmlElementDescriptor[] getElementsDescriptors(XmlTag context) {
    return myElementDescriptorsCache.get(null, this, context);
  }

  private static final SimpleFieldCache<XmlAttributeDescriptor[],BaseXmlElementDescriptorImpl> myAttributeDescriptorsCache =
    new SimpleFieldCache<XmlAttributeDescriptor[], BaseXmlElementDescriptorImpl>() {
    protected final XmlAttributeDescriptor[] compute(final BaseXmlElementDescriptorImpl xmlElementDescriptor) {
      return xmlElementDescriptor.collectAttributeDescriptors(null);
    }

    protected final XmlAttributeDescriptor[] getValue(final BaseXmlElementDescriptorImpl xmlElementDescriptor) {
      return xmlElementDescriptor.myAttributeDescriptors;
    }

    protected final void putValue(final XmlAttributeDescriptor[] xmlAttributeDescriptors, final BaseXmlElementDescriptorImpl xmlElementDescriptor) {
      xmlElementDescriptor.myAttributeDescriptors = xmlAttributeDescriptors;
    }
  };

  public XmlAttributeDescriptor[] getAttributesDescriptors(final XmlTag context) {
    return myAttributeDescriptorsCache.get(this);
  }

  // Read-only calculation
  protected abstract XmlAttributeDescriptor[] collectAttributeDescriptors(final XmlTag context);

  private static final SimpleFieldCache<HashMap<String,XmlAttributeDescriptor>, BaseXmlElementDescriptorImpl> attributeDescriptorsMapCache =
    new SimpleFieldCache<HashMap<String, XmlAttributeDescriptor>, BaseXmlElementDescriptorImpl>() {
      protected final HashMap<String, XmlAttributeDescriptor> compute(final BaseXmlElementDescriptorImpl baseXmlElementDescriptor) {
        return baseXmlElementDescriptor.collectAttributeDescriptorsMap(null);
      }

      protected final HashMap<String, XmlAttributeDescriptor> getValue(final BaseXmlElementDescriptorImpl baseXmlElementDescriptor) {
        return baseXmlElementDescriptor.attributeDescriptorsMap;
      }

      protected final void putValue(final HashMap<String, XmlAttributeDescriptor> hashMap, final BaseXmlElementDescriptorImpl baseXmlElementDescriptor) {
        baseXmlElementDescriptor.attributeDescriptorsMap = hashMap;
      }
    };

  public XmlAttributeDescriptor getAttributeDescriptor(String attributeName, final XmlTag context) {
    return attributeDescriptorsMapCache.get(this).get(attributeName);
  }

  // Read-only calculation
  protected abstract HashMap<String, XmlAttributeDescriptor> collectAttributeDescriptorsMap(final XmlTag context);

  private static final FieldCache<HashMap<String,XmlElementDescriptor>,BaseXmlElementDescriptorImpl,Object,XmlTag> myElementDescriptorsMapCache =
    new FieldCache<HashMap<String, XmlElementDescriptor>, BaseXmlElementDescriptorImpl, Object, XmlTag>() {
    protected final HashMap<String, XmlElementDescriptor> compute(final BaseXmlElementDescriptorImpl baseXmlElementDescriptor, final XmlTag p) {
      return baseXmlElementDescriptor.collectElementDescriptorsMap(p);
    }

    protected final HashMap<String, XmlElementDescriptor> getValue(final BaseXmlElementDescriptorImpl baseXmlElementDescriptor, final Object p) {
      return baseXmlElementDescriptor.myElementDescriptorsMap;
    }

    protected final void putValue(final HashMap<String, XmlElementDescriptor> hashMap,
                            final BaseXmlElementDescriptorImpl baseXmlElementDescriptor, final Object p) {
      baseXmlElementDescriptor.myElementDescriptorsMap = hashMap;
    }
  };

  public XmlElementDescriptor getElementDescriptor(XmlTag element, XmlTag contextTag){
    return myElementDescriptorsMapCache.get(null, this, element).get(element.getName());
  }

  public final XmlElementDescriptor getElementDescriptor(String name, XmlTag context){
    return myElementDescriptorsMapCache.get(null, this, context).get(name);
  }

  // Read-only calculation
  protected abstract HashMap<String, XmlElementDescriptor> collectElementDescriptorsMap(final XmlTag element);

  public final XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attr){
    return getAttributeDescriptor(attr.getName(), attr.getParent());
  }
}
