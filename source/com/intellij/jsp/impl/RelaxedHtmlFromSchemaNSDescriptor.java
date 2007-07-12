package com.intellij.jsp.impl;

import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RelaxedHtmlFromSchemaNSDescriptor extends XmlNSDescriptorImpl {
  public XmlElementDescriptor getElementDescriptor(@NotNull XmlTag tag) {
    XmlElementDescriptor elementDescriptor = super.getElementDescriptor(tag);

    if (elementDescriptor == null &&
        !tag.getNamespace().equals(XmlUtil.XHTML_URI)
       ) {
      return new AnyXmlElementDescriptor(null,tag.getNSDescriptor(tag.getNamespace(),true));
    }

    return elementDescriptor;
  }

  protected XmlElementDescriptor createElementDescriptor(final XmlTag tag) {
    return new RelaxedHtmlFromSchemaElementDescriptor(tag);
  }

  @NotNull
  public XmlElementDescriptor[] getRootElementsDescriptors(@Nullable final XmlDocument doc) {
    return ArrayUtil.mergeArrays(
      super.getRootElementsDescriptors(doc),
      HtmlUtil.getCustomTagDescriptors(doc),
      XmlElementDescriptor.class
    );
  }
}
