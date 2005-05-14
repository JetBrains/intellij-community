package com.intellij.jsp.impl;

import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.util.XmlUtil;
import com.intellij.psi.xml.XmlTag;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: May 14, 2005
 * Time: 10:24:35 PM
 * To change this template use File | Settings | File Templates.
 */
public class RelaxedNsXmlElementDescriptor extends XmlElementDescriptorImpl {
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
