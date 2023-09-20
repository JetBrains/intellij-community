// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.html;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.source.xml.XmlTagDelegate;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class HtmlTagDelegate extends XmlTagDelegate {

  public HtmlTagDelegate(@NotNull XmlTag tag) {
    super(tag);
  }

  @Override
  protected XmlTag @NotNull [] findSubTags(@NotNull String name, String namespace) {
    final XmlTag[] subTags = myTag.getSubTags();
    List<XmlTag> result = null;

    for (final XmlTag subTag : subTags) {
      if (namespace == null) {
        String tagName = subTag.getName();
        tagName = StringUtil.toLowerCase(tagName);

        if (name.equals(tagName)) {
          if (result == null) {
            result = new ArrayList<>(3);
          }

          result.add(subTag);
        }
      }
      else if (namespace.equals(subTag.getNamespace())
               && name.equals(subTag.getLocalName())
      ) {
        if (result == null) {
          result = new ArrayList<>(3);
        }

        result.add(subTag);
      }
    }

    return result == null ? XmlTag.EMPTY : result.toArray(XmlTag.EMPTY);
  }

  @Override
  protected void cacheOneAttributeValue(String name, String value, final Map<String, String> attributesValueMap) {
    name = StringUtil.toLowerCase(name);
    super.cacheOneAttributeValue(name, value, attributesValueMap);
  }

  @Override
  public String getPrefixByNamespace(String namespace) {
    if (XmlUtil.HTML_URI.equals(namespace)) namespace = XmlUtil.XHTML_URI;
    return super.getPrefixByNamespace(namespace);
  }

  @Override
  public String getAttributeValue(String qname) {
    qname = StringUtil.toLowerCase(qname);
    return super.getAttributeValue(qname);
  }

  @Override
  public String getAttributeValue(String name, String namespace) {
    name = StringUtil.toLowerCase(name);
    return super.getAttributeValue(name, namespace);
  }

  @Override
  public @NotNull String getNamespaceByPrefix(String prefix) {
    final String xmlNamespace = super.getNamespaceByPrefix(prefix);

    if (!prefix.isEmpty()) {
      return xmlNamespace;
    }

    if (xmlNamespace.isEmpty() || xmlNamespace.equals(XmlUtil.XHTML_URI)) {
      return XmlUtil.HTML_URI;
    }

    // ex.: mathML and SVG namespaces can be used inside html file
    return xmlNamespace;
  }
}
