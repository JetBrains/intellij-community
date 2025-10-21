// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.xml.XmlTagValueImpl;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagChild;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class IncludedXmlTag extends IncludedXmlElement<XmlTag> implements XmlTag {
  private static final Logger LOG = Logger.getInstance(IncludedXmlTag.class);
  public IncludedXmlTag(@NotNull XmlTag original, @Nullable PsiElement parent) {
    super(original, parent);
  }

  @Override
  public @Nullable XmlTag getParentTag() {
    return getParent() instanceof XmlTag ? (XmlTag)getParent() : null;
  }

  @Override
  public @NotNull @NonNls String getName() {
    return getOriginal().getName();
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    throw new UnsupportedOperationException("Can't modify included tags");
  }

  @Override
  public @NotNull @NonNls String getNamespace() {
    XmlTag original = getOriginal();
    LOG.assertTrue(original.isValid());
    return original.getNamespace();
  }

  @Override
  public @NotNull @NonNls String getLocalName() {
    return getOriginal().getLocalName();
  }

  @Override
  public @Nullable XmlElementDescriptor getDescriptor() {
    return getOriginal().getDescriptor();
  }

  @Override
  public XmlAttribute @NotNull [] getAttributes() {
    XmlAttribute[] original = getOriginal().getAttributes();
    XmlAttribute[] attributes = new XmlAttribute[original.length];
    for (int i = 0; i < original.length; i++) {
      XmlAttribute attribute = original[i];
      attributes[i] = new IncludedXmlAttribute(attribute, this);
    }
    return attributes;
  }

  @Override
  public @Nullable XmlAttribute getAttribute(@NonNls String name, @NonNls String namespace) {
    XmlAttribute attribute = getOriginal().getAttribute(name, namespace);
    return attribute == null ? null : new IncludedXmlAttribute(attribute, this);
  }

  @Override
  public @Nullable XmlAttribute getAttribute(@NonNls String qname) {
    XmlAttribute attribute = getOriginal().getAttribute(qname);
    return attribute == null ? null : new IncludedXmlAttribute(attribute, this);
  }

  @Override
  public @Nullable String getAttributeValue(@NonNls String name, @NonNls String namespace) {
    return getOriginal().getAttributeValue(name, namespace);
  }

  @Override
  public @Nullable String getAttributeValue(@NonNls String qname) {
    return getOriginal().getAttributeValue(qname);
  }

  @Override
  public XmlAttribute setAttribute(@NonNls String name, @NonNls String namespace, @NonNls String value) throws IncorrectOperationException {
    throw new UnsupportedOperationException("Can't modify included tags");
  }

  @Override
  public XmlAttribute setAttribute(@NonNls String qname, @NonNls String value) throws IncorrectOperationException {
    throw new UnsupportedOperationException("Can't modify included tags");
  }

  @Override
  public XmlTag createChildTag(@NonNls String localName,
                               @NonNls String namespace,
                               @Nullable @NonNls String bodyText,
                               boolean enforceNamespacesDeep) {
    return getOriginal().createChildTag(localName, namespace, bodyText, enforceNamespacesDeep);
  }

  @Override
  public XmlTag addSubTag(XmlTag subTag, boolean first) {
    throw new UnsupportedOperationException("Can't modify included tags");
  }

  @Override
  public XmlTag @NotNull [] getSubTags() {
    return wrapTags(getOriginal().getSubTags());
  }

  private XmlTag[] wrapTags(XmlTag[] original) {
    XmlTag[] result = new XmlTag[original.length];
    for (int i = 0; i < original.length; i++) {
      result[i] = new IncludedXmlTag(original[i], this);
    }
    return result;
  }

  @Override
  public XmlTag @NotNull [] findSubTags(@NonNls String qname) {
    return wrapTags(getOriginal().findSubTags(qname));
  }

  @Override
  public XmlTag @NotNull [] findSubTags(@NonNls String localName, @NonNls String namespace) {
    return wrapTags(getOriginal().findSubTags(localName, namespace));
  }

  @Override
  public @Nullable XmlTag findFirstSubTag(@NonNls String qname) {
    XmlTag tag = getOriginal().findFirstSubTag(qname);
    return tag == null ? null : new IncludedXmlTag(tag, this);
  }

  @Override
  public @NotNull @NonNls String getNamespacePrefix() {
    return getOriginal().getNamespacePrefix();
  }

  @Override
  public @NotNull @NonNls String getNamespaceByPrefix(@NonNls String prefix) {
    return getOriginal().getNamespaceByPrefix(prefix);
  }

  @Override
  public @Nullable String getPrefixByNamespace(@NonNls String namespace) {
    return getOriginal().getPrefixByNamespace(namespace);
  }

  @Override
  public String[] knownNamespaces() {
    return getOriginal().knownNamespaces();
  }

  @Override
  public boolean hasNamespaceDeclarations() {
    return getOriginal().hasNamespaceDeclarations();
  }

  @Override
  public @NotNull Map<String, String> getLocalNamespaceDeclarations() {
    return getOriginal().getLocalNamespaceDeclarations();
  }

  @Override
  public @NotNull XmlTagValue getValue() {
    return XmlTagValueImpl.createXmlTagValue(this);
  }

  @Override
  public @Nullable XmlNSDescriptor getNSDescriptor(@NonNls String namespace, boolean strict) {
    return getOriginal().getNSDescriptor(namespace, strict);
  }

  @Override
  public boolean isEmpty() {
    return getOriginal().isEmpty();
  }

  @Override
  public void collapseIfEmpty() {
    throw new UnsupportedOperationException("Can't modify included tags");
  }

  @Override
  public @Nullable @NonNls String getSubTagText(@NonNls String qname) {
    return getOriginal().getSubTagText(qname);
  }

  @Override
  public PsiMetaData getMetaData() {
    return null;
  }

  @Override
  public XmlTagChild getNextSiblingInTag() {
    return null;
  }

  @Override
  public XmlTagChild getPrevSiblingInTag() {
    return null;
  }
}
