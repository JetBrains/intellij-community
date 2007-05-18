package com.intellij.jsp.impl;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: May 14, 2005
 * Time: 10:24:35 PM
 * To change this template use File | Settings | File Templates.
 */
public class RelaxedNsXmlElementDescriptor extends XmlElementDescriptorImpl {
  @NonNls private static final String JSFC_ATTR_NAME = "jsfc";

  RelaxedNsXmlElementDescriptor(XmlTag tag) {
    super(tag);
  }

  public XmlElementDescriptor getElementDescriptor(XmlTag childTag) {
    XmlElementDescriptor elementDescriptor = super.getElementDescriptor(childTag);

    if (elementDescriptor == null) {
      final String namespace = childTag.getNamespace();

      if(!XmlUtil.XHTML_URI.equals(namespace)) {
        return new AnyXmlElementDescriptor(this,childTag.getNSDescriptor(childTag.getNamespace(),true));
      }
    }

    return elementDescriptor;
  }

  private static final XmlElementDescriptor findElementDescriptorFromString(String str, XmlTag context) {
    final String namespaceByPrefix = context.getNamespaceByPrefix(XmlUtil.findPrefixByQualifiedName(str));
    final XmlNSDescriptor nsDescriptor = context.getNSDescriptor(namespaceByPrefix, true);
    if (nsDescriptor instanceof TldDescriptor) {
      return ((TldDescriptor)nsDescriptor).getElementDescriptor(XmlUtil.findLocalNameByQualifiedName(str));
    }
    return null;
  }

  public XmlAttributeDescriptor[] getAttributesDescriptors(final XmlTag context) {
    final XmlAttributeDescriptor[] attributeDescriptors = super.getAttributesDescriptors(context);
    final String jsfc = context != null ? context.getAttributeValue(JSFC_ATTR_NAME):null;
    if (jsfc == null) return attributeDescriptors;
    final XmlElementDescriptor descriptor = findElementDescriptorFromString(jsfc, context);

    if (descriptor != null) {
      return ArrayUtil.mergeArrays(attributeDescriptors, descriptor.getAttributesDescriptors(context), XmlAttributeDescriptor.class);
    }
    return attributeDescriptors;
  }

  public XmlAttributeDescriptor getAttributeDescriptor(String attributeName, final XmlTag context) {
    final String jsfc = context != null ? context.getAttributeValue(JSFC_ATTR_NAME):null;

    final XmlAttributeDescriptor descriptor = super.getAttributeDescriptor(attributeName.toLowerCase(), context);
    if (jsfc == null || descriptor != null) return descriptor;

    final XmlElementDescriptor xmlElementDescriptor = findElementDescriptorFromString(jsfc, context);
    if (xmlElementDescriptor != null) return xmlElementDescriptor.getAttributeDescriptor(attributeName, context);

    return null;
  }

  public boolean allowElementsFromNamespace(final String namespace, final XmlTag context) {
    return true;
  }
}
