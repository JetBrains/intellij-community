// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.html;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.xml.XmlStubBasedTag;
import com.intellij.psi.impl.source.xml.XmlTagDelegate;
import com.intellij.psi.impl.source.xml.stub.XmlTagStubImpl;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HtmlStubBasedTagImpl extends XmlStubBasedTag implements HtmlTag {
  public HtmlStubBasedTagImpl(@NotNull XmlTagStubImpl stub,
                              @NotNull IStubElementType<? extends XmlTagStubImpl, ? extends HtmlStubBasedTagImpl> nodeType) {
    super(stub, nodeType);
  }

  public HtmlStubBasedTagImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  @NotNull
  public XmlTag[] findSubTags(@NotNull String name, String namespace) {
    final XmlTag[] subTags = getSubTags();
    List<XmlTag> result = null;

    for (final XmlTag subTag : subTags) {
      if (namespace == null) {
        String tagName = subTag.getName();
        tagName = StringUtil.toLowerCase(tagName);

        if (name == null || name.equals(tagName)) {
          if (result == null) {
            result = new ArrayList<>(3);
          }

          result.add(subTag);
        }
      }
      else if (namespace.equals(subTag.getNamespace()) &&
               (name == null || name.equals(subTag.getLocalName()))
      ) {
        if (result == null) {
          result = new ArrayList<>(3);
        }

        result.add(subTag);
      }
    }

    return result == null ? EMPTY : result.toArray(XmlTag.EMPTY);
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
  @NotNull
  public String getNamespace() {
    final String xmlNamespace = super.getNamespace();

    if (!getNamespacePrefix().isEmpty()) {
      return xmlNamespace;
    }

    if (xmlNamespace.isEmpty() || xmlNamespace.equals(XmlUtil.XHTML_URI)) {
      return XmlUtil.HTML_URI;
    }

    // ex.: mathML and SVG namespaces can be used inside html file
    return xmlNamespace;
  }

  @Nullable
  @Override
  public String getRealNs(@Nullable final String value) {
    if (XmlUtil.XHTML_URI.equals(value)) return XmlUtil.HTML_URI;
    return value;
  }

  @Override
  public String toString() {
    return "HtmlTag:" + getName();
  }

  @Override
  public String getPrefixByNamespace(String namespace) {
    if (XmlUtil.HTML_URI.equals(namespace)) namespace = XmlUtil.XHTML_URI;
    return super.getPrefixByNamespace(namespace);
  }

  @Override
  public XmlTag getParentTag() {
    return PsiTreeUtil.getParentOfType(this, XmlTag.class);
  }

  @NotNull
  @Override
  protected XmlTagDelegate createDelegate() {
    return new HtmlTagImplDelegate();
  }

  private class HtmlTagImplDelegate extends XmlStubBasedTagDelegate {
    @Override
    protected void cacheOneAttributeValue(String name, String value, final Map<String, String> attributesValueMap) {
      name = StringUtil.toLowerCase(name);
      super.cacheOneAttributeValue(name, value, attributesValueMap);
    }
  }

}
