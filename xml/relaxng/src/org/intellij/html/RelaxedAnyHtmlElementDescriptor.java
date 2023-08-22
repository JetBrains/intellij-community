// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.html;

import com.intellij.html.impl.RelaxedHtmlFromSchemaElementDescriptor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;

public class RelaxedAnyHtmlElementDescriptor extends AnyXmlElementDescriptor {

  public RelaxedAnyHtmlElementDescriptor(XmlElementDescriptor parentDescriptor,
                                         XmlNSDescriptor xmlNSDescriptor) {
    super(parentDescriptor, xmlNSDescriptor);
  }

  @Override
  public XmlAttributeDescriptor[] getAttributesDescriptors(XmlTag context) {
    return RelaxedHtmlFromSchemaElementDescriptor.addAttrDescriptorsForFacelets(context, XmlAttributeDescriptor.EMPTY);
  }

  @Override
  public XmlAttributeDescriptor getAttributeDescriptor(String attributeName, XmlTag context) {
    XmlAttributeDescriptor descriptor = RelaxedHtmlFromSchemaElementDescriptor.getAttributeDescriptorFromFacelets(attributeName, context);
    if (descriptor == null) {
      descriptor = super.getAttributeDescriptor(attributeName, context);
    }
    return descriptor;
  }

  @Override
  public XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attr) {
    XmlAttributeDescriptor descriptor = RelaxedHtmlFromSchemaElementDescriptor.getAttributeDescriptorFromFacelets(attr.getName(), attr.getParent());
    if (descriptor == null) {
      descriptor = super.getAttributeDescriptor(attr);
    }
    return descriptor;
  }
}
