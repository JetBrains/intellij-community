package com.intellij.jsp.impl;

import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.util.XmlUtil;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import com.intellij.psi.xml.XmlTag;

/**
 * Class to support any xml element descriptor from other namespace
 */
public class RelaxedNsXmlNSDescriptor extends XmlNSDescriptorImpl {
  public XmlElementDescriptor getElementDescriptor(XmlTag tag) {
    XmlElementDescriptor elementDescriptor = super.getElementDescriptor(tag);

    if (elementDescriptor == null &&
        !tag.getNamespace().equals(XmlUtil.XHTML_URI)
       ) {
      return new AnyXmlElementDescriptor(null,tag.getNSDescriptor(tag.getNamespace(),true));
    }

    return elementDescriptor;
  }

  protected XmlElementDescriptorImpl createElementDescriptor(final XmlTag tag) {
    return new RelaxedNsXmlElementDescriptor(tag);
  }

  public static class RelaxedNsXmlElementDescriptor extends XmlElementDescriptorImpl {

    public RelaxedNsXmlElementDescriptor() {}

    RelaxedNsXmlElementDescriptor(XmlTag tag) {
      super(tag);
    }

    public XmlElementDescriptor getElementDescriptor(XmlTag childTag) {
      XmlElementDescriptor elementDescriptor = super.getElementDescriptor(childTag);

      if (elementDescriptor == null &&
          !childTag.getNamespace().equals(XmlUtil.XHTML_URI)
        ) {
        return new AnyXmlElementDescriptor(this,childTag.getNSDescriptor(childTag.getNamespace(),true));
      }

      return elementDescriptor;
    }
  }
}
