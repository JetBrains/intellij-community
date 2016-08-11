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
package com.intellij.psi.impl.source.html.dtd;

import com.intellij.html.impl.RelaxedHtmlFromSchemaElementDescriptor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.dtd.BaseXmlElementDescriptorImpl;
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;

import java.util.HashMap;

/**
 * @by Maxim.Mossienko
 */
public class HtmlElementDescriptorImpl extends BaseXmlElementDescriptorImpl {
  private final XmlElementDescriptor myDelegate;
  private final boolean myRelaxed;
  private final boolean myCaseSensitive;

  public HtmlElementDescriptorImpl(XmlElementDescriptor _delegate, boolean relaxed, boolean caseSensitive) {
    myDelegate = _delegate;
    myRelaxed = relaxed;
    myCaseSensitive = caseSensitive;
  }

  @Override
  public String getQualifiedName() {
    return myDelegate.getQualifiedName();
  }

  @Override
  public String getDefaultName() {
    return myDelegate.getDefaultName();
  }

  // Read-only calculation
  @Override
  protected final XmlElementDescriptor[] doCollectXmlDescriptors(final XmlTag context) {
    XmlElementDescriptor[] elementsDescriptors = myDelegate.getElementsDescriptors(context);
    XmlElementDescriptor[] temp = new XmlElementDescriptor[elementsDescriptors.length];

    for (int i = 0; i < elementsDescriptors.length; i++) {
      temp[i] = new HtmlElementDescriptorImpl( elementsDescriptors[i], myRelaxed, myCaseSensitive );
    }
    return temp;
  }

  @Override
  public XmlElementDescriptor getElementDescriptor(XmlTag element, XmlTag contextTag) {
    String name = element.getName();
    if (!myCaseSensitive) name = name.toLowerCase();

    XmlElementDescriptor xmlElementDescriptor = getElementDescriptor(name, element);
    if (xmlElementDescriptor == null && "html".equals(getName())) {
      XmlTag head = null;
      XmlTag body = null;

      for (XmlTag child : PsiTreeUtil.getChildrenOfTypeAsList(contextTag, XmlTag.class)) {
        if ("head".equals(child.getName())) head = child;
        if ("body".equals(child.getName())) body = child;
      }
      if (head == null) {
        if (body == null || element.getTextOffset() < body.getTextOffset()) {
          XmlElementDescriptor headDescriptor = getElementDescriptor("head", contextTag);
          if (headDescriptor != null) {
            xmlElementDescriptor = headDescriptor.getElementDescriptor(element, contextTag);
          }
        }
      }
      if (xmlElementDescriptor == null && body == null) {
        XmlElementDescriptor bodyDescriptor = getElementDescriptor("body", contextTag);
        if (bodyDescriptor != null) {
          xmlElementDescriptor = bodyDescriptor.getElementDescriptor(element, contextTag);
        }
      }

    }
    if (xmlElementDescriptor == null && myRelaxed) {
      xmlElementDescriptor = RelaxedHtmlFromSchemaElementDescriptor.getRelaxedDescriptor(this, element);
    }

    return xmlElementDescriptor;
  }

  // Read-only calculation
  @Override
  protected HashMap<String, XmlElementDescriptor> collectElementDescriptorsMap(final XmlTag element) {
    final HashMap<String, XmlElementDescriptor> hashMap = new HashMap<>();
    final XmlElementDescriptor[] elementDescriptors = myDelegate.getElementsDescriptors(element);

    for (XmlElementDescriptor elementDescriptor : elementDescriptors) {
      hashMap.put(elementDescriptor.getName(), new HtmlElementDescriptorImpl(elementDescriptor, myRelaxed, myCaseSensitive));
    }
    return hashMap;
  }

  // Read-only calculation
  @Override
  protected XmlAttributeDescriptor[] collectAttributeDescriptors(final XmlTag context) {
    final XmlAttributeDescriptor[] attributesDescriptors = myDelegate.getAttributesDescriptors(context);
    XmlAttributeDescriptor[] temp = new XmlAttributeDescriptor[attributesDescriptors.length];

    for (int i = 0; i < attributesDescriptors.length; i++) {
      temp[i] = new HtmlAttributeDescriptorImpl(attributesDescriptors[i], myCaseSensitive);
    }
    return temp;
  }

  @Override
  public XmlAttributeDescriptor getAttributeDescriptor(String attributeName, final XmlTag context) {
    String caseSensitiveAttributeName =  !myCaseSensitive ? attributeName.toLowerCase() : attributeName;
    XmlAttributeDescriptor descriptor = super.getAttributeDescriptor(caseSensitiveAttributeName, context);
    if (descriptor == null) descriptor = RelaxedHtmlFromSchemaElementDescriptor.getAttributeDescriptorFromFacelets(attributeName, context);
    
    if (descriptor == null) {
      String prefix = XmlUtil.findPrefixByQualifiedName(attributeName);
      
      if ("xml".equals(prefix)) { // todo this is not technically correct dtd document references namespaces but we should handle it at least for xml stuff
        XmlNSDescriptor nsdescriptor = context.getNSDescriptor(XmlUtil.XML_NAMESPACE_URI, true);
        if (nsdescriptor instanceof XmlNSDescriptorImpl) {
          descriptor = ((XmlNSDescriptorImpl)nsdescriptor).getAttribute(
            XmlUtil.findLocalNameByQualifiedName(caseSensitiveAttributeName), XmlUtil.XML_NAMESPACE_URI, context);
        }
      }
    }
    if (descriptor == null && HtmlUtil.isHtml5Context(context)) {
      descriptor = myDelegate.getAttributeDescriptor(attributeName, context);
    }
    return descriptor;
  }

  // Read-only calculation
  @Override
  protected HashMap<String, XmlAttributeDescriptor> collectAttributeDescriptorsMap(final XmlTag context) {
    final HashMap<String, XmlAttributeDescriptor> hashMap = new HashMap<>();
    XmlAttributeDescriptor[] elementAttributeDescriptors = myDelegate.getAttributesDescriptors(context);

    for (final XmlAttributeDescriptor attributeDescriptor : elementAttributeDescriptors) {
      hashMap.put(
        attributeDescriptor.getName(),
        new HtmlAttributeDescriptorImpl(attributeDescriptor, myCaseSensitive)
      );
    }
    return hashMap;
  }

  @Override
  public XmlNSDescriptor getNSDescriptor() {
    return myDelegate.getNSDescriptor();
  }

  @Override
  public int getContentType() {
    return myDelegate.getContentType();
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

  @Override
  public Object[] getDependences() {
    return myDelegate.getDependences();
  }

  @Override
  public XmlAttributeDescriptor[] getAttributesDescriptors(final XmlTag context) {
    return RelaxedHtmlFromSchemaElementDescriptor.addAttrDescriptorsForFacelets(context, getDefaultAttributeDescriptors(context));
  }

  public XmlAttributeDescriptor[] getDefaultAttributeDescriptors(XmlTag context) {
    return super.getAttributesDescriptors(context);
  }

  public boolean allowElementsFromNamespace(final String namespace, final XmlTag context) {
    return true;
  }

  @Override
  public String toString() {
    return myDelegate.toString();
  }
}
