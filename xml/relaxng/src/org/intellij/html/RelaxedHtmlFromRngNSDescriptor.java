// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.html;

import com.intellij.html.RelaxedHtmlNSDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import org.intellij.plugins.relaxNG.model.descriptors.RngNsDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.xml.util.HtmlUtil.MATH_ML_NAMESPACE;
import static com.intellij.xml.util.HtmlUtil.SVG_NAMESPACE;
import static com.intellij.xml.util.XmlUtil.HTML_URI;

public class RelaxedHtmlFromRngNSDescriptor extends RngNsDescriptor implements RelaxedHtmlNSDescriptor {
  private static final Logger LOG = Logger.getInstance(RelaxedHtmlFromRngNSDescriptor.class);

  @Override
  public XmlElementDescriptor getElementDescriptor(@NotNull XmlTag tag) {
    XmlElementDescriptor elementDescriptor = super.getElementDescriptor(tag);

    if (LOG.isDebugEnabled()) {
      LOG.debug("Descriptor from rng for tag " +
                tag.getName() +
                " is " +
                (elementDescriptor != null ? elementDescriptor.getClass().getCanonicalName() : "NULL"));
    }

    String namespace;
    if (elementDescriptor == null &&
        !((namespace = tag.getNamespace()).equals(XmlUtil.XHTML_URI))) {
      var nsDescriptor = HTML_URI.equals(namespace) ? this : tag.getNSDescriptor(namespace, true);
      if (HTML_URI.equals(namespace) || MATH_ML_NAMESPACE.equals(namespace) || SVG_NAMESPACE.equals(namespace)) {
        return new RelaxedAnyHtmlElementDescriptor(null, nsDescriptor);
      }
      else {
        return new AnyXmlElementDescriptor(null, nsDescriptor);
      }
    }

    return elementDescriptor;
  }

  @Override
  protected XmlElementDescriptor initDescriptor(@NotNull XmlElementDescriptor descriptor) {
    return new RelaxedHtmlFromRngElementDescriptor(descriptor);
  }

  @Override
  public XmlElementDescriptor @NotNull [] getRootElementsDescriptors(final @Nullable XmlDocument doc) {
    final XmlElementDescriptor[] descriptors = super.getRootElementsDescriptors(doc);
    List<XmlElementDescriptor> rootElements = ContainerUtil.append(
      ContainerUtil.filter(descriptors, descriptor -> isRootTag((RelaxedHtmlFromRngElementDescriptor)descriptor)),
    HtmlUtil.getCustomTagDescriptors(doc));
    return rootElements.toArray(XmlElementDescriptor.EMPTY_ARRAY);
  }

  @Override
  public XmlElementDescriptor @NotNull [] getAllElementsDescriptors(@Nullable XmlDocument document) {
    return super.getRootElementsDescriptors(document);
  }

  protected boolean isRootTag(RelaxedHtmlFromRngElementDescriptor descriptor) {
    return descriptor.isHtml() ||
           "svg".equals(descriptor.getName()) ||
           "math".equals(descriptor.getName());
  }

  @Override
  public XmlElementDescriptor getElementDescriptor(String localName, String namespace) {
    XmlElementDescriptor descriptor = super.getElementDescriptor(localName, namespace);
    if (descriptor != null) return descriptor;
    descriptor = super.getElementDescriptor(localName, MATH_ML_NAMESPACE);
    if (descriptor != null) return descriptor;
    return super.getElementDescriptor(localName, SVG_NAMESPACE);
  }
}
