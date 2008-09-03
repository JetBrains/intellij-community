package com.intellij.html.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
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
  private static final Logger LOG = Logger.getInstance("#com.intellij.html.impl.RelaxedHtmlFromSchemaNSDescriptor");
  private static final Key<Boolean> TEMP_DEBUG_KEY = Key.create("IDEADEV-26043");

  public XmlElementDescriptor getElementDescriptor(@NotNull XmlTag tag) {
    XmlElementDescriptor elementDescriptor = super.getElementDescriptor(tag);

    if (elementDescriptor == null && !tag.getNamespace().equals(XmlUtil.XHTML_URI)) {
      final Boolean data = tag.getUserData(TEMP_DEBUG_KEY);
      if (data != null && !data.booleanValue()) {
        LOG.error("Looks like we got an infinite loop for tag: " + tag.getText() + " and file: " + tag.getContainingFile().getText());
      }

      tag.putUserData(TEMP_DEBUG_KEY, data == null);
      return new AnyXmlElementDescriptor(null, tag.getNSDescriptor(tag.getNamespace(), true));
    }

    return elementDescriptor;
  }

  protected XmlElementDescriptor createElementDescriptor(final XmlTag tag) {
    return new RelaxedHtmlFromSchemaElementDescriptor(tag);
  }

  @NotNull
  public XmlElementDescriptor[] getRootElementsDescriptors(@Nullable final XmlDocument doc) {
    return ArrayUtil.mergeArrays(super.getRootElementsDescriptors(doc), HtmlUtil.getCustomTagDescriptors(doc), XmlElementDescriptor.class);
  }
}
