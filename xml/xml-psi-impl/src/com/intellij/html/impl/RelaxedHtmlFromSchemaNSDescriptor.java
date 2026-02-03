// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.impl;

import com.intellij.html.RelaxedHtmlNSDescriptor;
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

public class RelaxedHtmlFromSchemaNSDescriptor extends XmlNSDescriptorImpl implements RelaxedHtmlNSDescriptor {
  @Override
  public XmlElementDescriptor getElementDescriptor(@NotNull XmlTag tag) {
    XmlElementDescriptor elementDescriptor = super.getElementDescriptor(tag);

    String namespace;
    if (elementDescriptor == null && 
        !((namespace = tag.getNamespace()).equals(XmlUtil.XHTML_URI))) {
      return new AnyXmlElementDescriptor(
        null, 
        XmlUtil.HTML_URI.equals(namespace) ? this : tag.getNSDescriptor(tag.getNamespace(), true)
      );
    }

    return elementDescriptor;
  }

  @Override
  protected XmlElementDescriptor createElementDescriptor(final XmlTag tag) {
    return new RelaxedHtmlFromSchemaElementDescriptor(tag);
  }

  @Override
  public XmlElementDescriptor @NotNull [] getRootElementsDescriptors(final @Nullable XmlDocument doc) {
    return ArrayUtil.mergeArrays(super.getRootElementsDescriptors(doc), HtmlUtil.getCustomTagDescriptors(doc));
  }
}
