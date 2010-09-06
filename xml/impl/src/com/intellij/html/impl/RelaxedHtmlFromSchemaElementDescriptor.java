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
package com.intellij.html.impl;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.XmlAttributeDescriptorsProvider;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author Maxim.Mossienko
 */
public class RelaxedHtmlFromSchemaElementDescriptor extends XmlElementDescriptorImpl {

  RelaxedHtmlFromSchemaElementDescriptor(XmlTag tag) {
    super(tag);
  }

  public XmlElementDescriptor getElementDescriptor(XmlTag childTag, XmlTag contextTag) {
    XmlElementDescriptor elementDescriptor = super.getElementDescriptor(childTag, contextTag);

    if (elementDescriptor == null) {
      return getRelaxedDescriptor(this, childTag);
    }

    return elementDescriptor;
  }

  public XmlElementDescriptor[] getElementsDescriptors(final XmlTag context) {
    return ArrayUtil.mergeArrays(
      super.getElementsDescriptors(context),
      HtmlUtil.getCustomTagDescriptors(context), 
      XmlElementDescriptor.class
    );
  }

  public static XmlElementDescriptor getRelaxedDescriptor(XmlElementDescriptor base, final XmlTag childTag) {
    final String namespace = childTag.getNamespace();
    final XmlExtension extension = XmlExtension.getExtensionByElement(childTag);
    if(!XmlUtil.XHTML_URI.equals(namespace) && 
       ( base.getContentType() != XmlElementDescriptor.CONTENT_TYPE_EMPTY ||
         (extension != null && extension.isCustomTagAllowed(childTag)) // allow custom tag
       ) ) {
      return new AnyXmlElementDescriptor(base,childTag.getNSDescriptor(namespace,true));
    }
    return null;
  }

  public XmlAttributeDescriptor[] getAttributesDescriptors(final XmlTag context) {
    return addAttrDescriptorsForFacelets(context, super.getAttributesDescriptors(context));
  }

  public static XmlAttributeDescriptor[] addAttrDescriptorsForFacelets(final XmlTag context,
                                                                       XmlAttributeDescriptor[] descriptors) {
    if (context == null) {
      return descriptors;
    }
    for (XmlAttributeDescriptorsProvider provider: Extensions.getExtensions(XmlAttributeDescriptorsProvider.EP_NAME)) {
      descriptors = ArrayUtil.mergeArrays(descriptors, provider.getAttributeDescriptors(context), XmlAttributeDescriptor.class);
    }
    return descriptors;
  }

  @Override
  public XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attribute) {
    return getAttributeDescriptor(attribute.getName(), attribute.getParent());
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

  public XmlAttributeDescriptor getAttributeDescriptor(String attributeName, final XmlTag context) {
    final XmlAttributeDescriptor descriptor = super.getAttributeDescriptor(attributeName.toLowerCase(), context);
    if (descriptor != null) return descriptor;

    return getAttributeDescriptorFromFacelets(attributeName, context);
  }

  @Nullable
  public static XmlAttributeDescriptor getAttributeDescriptorFromFacelets(final String attributeName, final XmlTag context) {
    if (context == null) {
      return null;
    }
    for (XmlAttributeDescriptorsProvider provider: Extensions.getExtensions(XmlAttributeDescriptorsProvider.EP_NAME)) {
      final XmlAttributeDescriptor descriptor = provider.getAttributeDescriptor(attributeName, context);
      if (descriptor != null) {
        return descriptor;
      }
    }
    return null;
  }

  public boolean allowElementsFromNamespace(final String namespace, final XmlTag context) {
    return true;
  }
}
