// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.xml;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public interface XmlTag extends XmlElement, PsiNamedElement, PsiMetaOwner, XmlTagChild, XmlNamedReferenceHost {
  XmlTag[] EMPTY = new XmlTag[0];

  @Override
  @NotNull @NlsSafe String getName();
  @NotNull @NlsSafe String getNamespace();
  @NotNull @NlsSafe String getLocalName();

  @Nullable XmlElementDescriptor getDescriptor();

  XmlAttribute @NotNull [] getAttributes();

  @Nullable XmlAttribute getAttribute(@NlsSafe String name, @NlsSafe String namespace);

  /**
   * Returns a tag attribute by qualified name.
   *
   * @param qname qualified attribute name, like "ns:name" or "name".
   * @return null if the attribute not exists.
   * @see #getAttribute(String, String)
   */
  @Nullable XmlAttribute getAttribute(@NlsSafe String qname);

  @Nullable @NlsSafe String getAttributeValue(@NlsSafe String name, @NlsSafe String namespace);

  /**
   * Returns a tag attribute value by qualified name.
   *
   * @param qname qualified attribute name, like "ns:name" or "name".
   * @return null if the attribute not exists.
   * @see #getAttributeValue(String, String)
   */
  @Nullable @NlsSafe String getAttributeValue(@NlsSafe String qname);

  XmlAttribute setAttribute(@NlsSafe String name, @NlsSafe String namespace, @NlsSafe String value) throws IncorrectOperationException;
  XmlAttribute setAttribute(@NlsSafe String qname, @NlsSafe String value) throws IncorrectOperationException;

  /**
   * Creates a new child tag
   * @param localName new tag's name
   * @param namespace new tag's namespace
   * @param bodyText pass null to create collapsed tag, empty string means creating expanded one
   * @param enforceNamespacesDeep if you pass some XML tags to {@code bodyText} parameter, this flag sets namespace prefixes for them
   * @return created tag. Use {@link #addSubTag(XmlTag, boolean)}} to add it to parent
   */
  XmlTag createChildTag(@NlsSafe String localName, @NlsSafe String namespace, @Nullable @NlsSafe String bodyText, boolean enforceNamespacesDeep);
  XmlTag addSubTag(XmlTag subTag, boolean first);

  XmlTag @NotNull [] getSubTags();
  XmlTag @NotNull [] findSubTags(@NlsSafe String qname);

  /**
   * @param localName non-qualified tag name.
   * @param namespace if null, name treated as qualified name to find.
   */
  XmlTag @NotNull [] findSubTags(@NlsSafe String localName, @Nullable @NlsSafe String namespace);

  @Nullable XmlTag findFirstSubTag(@NlsSafe String qname);

  @NotNull @NlsSafe String getNamespacePrefix();
  @NotNull @NlsSafe String getNamespaceByPrefix(@NlsSafe String prefix);
  @Nullable String getPrefixByNamespace(@NlsSafe String namespace);
  String[] knownNamespaces();

  boolean hasNamespaceDeclarations();

  /**
   * @return map keys: prefixes values: namespaces
   */
  @NotNull Map<String, String> getLocalNamespaceDeclarations();

  @NotNull XmlTagValue getValue();

  @Nullable XmlNSDescriptor getNSDescriptor(@NlsSafe String namespace, boolean strict);

  boolean isEmpty();

  void collapseIfEmpty();

  @Nullable @NlsSafe
  String getSubTagText(@NlsSafe String qname);

  default boolean isCaseSensitive() {
    return true;
  }

  default @Nullable @NlsSafe String getRealNs(@Nullable String value) {
    return value;
  }

  @Experimental
  @Override
  default @Nullable @NlsSafe String getHostName() {
    return getLocalName();
  }
}
