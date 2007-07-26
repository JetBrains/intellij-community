package com.intellij.jsp.impl;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;

/**
 * @author Maxim.Mossienko
 */
public class RelaxedHtmlFromSchemaElementDescriptor extends XmlElementDescriptorImpl {
  @NonNls private static final String JSFC_ATTR_NAME = "jsfc";

  RelaxedHtmlFromSchemaElementDescriptor(XmlTag tag) {
    super(tag);
  }

  public XmlElementDescriptor getElementDescriptor(XmlTag childTag) {
    XmlElementDescriptor elementDescriptor = super.getElementDescriptor(childTag);

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

    if(!XmlUtil.XHTML_URI.equals(namespace)) {
      return new AnyXmlElementDescriptor(base,childTag.getNSDescriptor(childTag.getNamespace(),true));
    }
    return null;
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
    return addAttrDescriptorsForFacelets(context, super.getAttributesDescriptors(context));
  }

  public static XmlAttributeDescriptor[] addAttrDescriptorsForFacelets(final XmlTag context,
                                                                       final XmlAttributeDescriptor[] attributeDescriptors) {
    final String jsfc = context != null ? context.getAttributeValue(JSFC_ATTR_NAME):null;
    if (jsfc == null) return attributeDescriptors;
    final XmlElementDescriptor descriptor = findElementDescriptorFromString(jsfc, context);

    if (descriptor != null) {
      return ArrayUtil.mergeArrays(attributeDescriptors, descriptor.getAttributesDescriptors(context), XmlAttributeDescriptor.class);
    }
    return attributeDescriptors;
  }

  public XmlAttributeDescriptor getAttributeDescriptor(String attributeName, final XmlTag context) {
    final XmlAttributeDescriptor descriptor = super.getAttributeDescriptor(attributeName.toLowerCase(), context);
    if (descriptor != null) return descriptor;

    return getAttributeDescriptorFromFacelets(attributeName, context);
  }

  public static XmlAttributeDescriptor getAttributeDescriptorFromFacelets(final String attributeName, final XmlTag context) {
    final String jsfc = context != null ? context.getAttributeValue(JSFC_ATTR_NAME):null;

    final XmlElementDescriptor xmlElementDescriptor = jsfc != null ? findElementDescriptorFromString(jsfc, context):null;
    if (xmlElementDescriptor != null) return xmlElementDescriptor.getAttributeDescriptor(attributeName, context);

    return null;
  }

  public boolean allowElementsFromNamespace(final String namespace, final XmlTag context) {
    return true;
  }
}
