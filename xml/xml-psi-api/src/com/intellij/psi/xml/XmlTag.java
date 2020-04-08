// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.xml;

import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public interface XmlTag extends XmlElement, PsiNamedElement, PsiMetaOwner, XmlTagChild, XmlNamedReferenceHost {
  XmlTag[] EMPTY = new XmlTag[0];

  @Override
  @NotNull @NonNls String getName();
  @NotNull @NonNls String getNamespace();
  @NotNull @NonNls String getLocalName();

  @Nullable XmlElementDescriptor getDescriptor();

  XmlAttribute @NotNull [] getAttributes();

  @Nullable XmlAttribute getAttribute(@NonNls String name, @NonNls String namespace);

  /**
   * Returns a tag attribute by qualified name.
   *
   * @param qname qualified attribute name, like "ns:name" or "name".
   * @return null if the attribute not exist.
   * @see #getAttribute(String, String)
   */
  @Nullable XmlAttribute getAttribute(@NonNls String qname);

  @Nullable String getAttributeValue(@NonNls String name, @NonNls String namespace);

  /**
   * Returns a tag attribute value by qualified name.
   *
   * @param qname qualified attribute name, like "ns:name" or "name".
   * @return null if the attribute not exist.
   * @see #getAttributeValue(String, String)
   */
  @Nullable String getAttributeValue(@NonNls String qname);

  XmlAttribute setAttribute(@NonNls String name, @NonNls String namespace, @NonNls String value) throws IncorrectOperationException;
  XmlAttribute setAttribute(@NonNls String qname, @NonNls String value) throws IncorrectOperationException;

  /**
   * Creates a new child tag
   * @param localName new tag's name
   * @param namespace new tag's namespace
   * @param bodyText pass null to create collapsed tag, empty string means creating expanded one
   * @param enforceNamespacesDeep if you pass some xml tags to {@code bodyText} parameter, this flag sets namespace prefixes for them
   * @return created tag. Use {@link #addSubTag(XmlTag, boolean)}} to add it to parent
   */
  XmlTag createChildTag(@NonNls String localName, @NonNls String namespace, @Nullable @NonNls String bodyText, boolean enforceNamespacesDeep);
  XmlTag addSubTag(XmlTag subTag, boolean first);

  XmlTag @NotNull [] getSubTags();
  XmlTag @NotNull [] findSubTags(@NonNls String qname);

  /**
   * @param localName non-qualified tag name.
   * @param namespace if null, name treated as qualified name to find.
   */
  XmlTag @NotNull [] findSubTags(@NonNls String localName, @Nullable String namespace);

  @Nullable XmlTag findFirstSubTag(@NonNls String qname);

  @NotNull @NonNls String getNamespacePrefix();
  @NotNull @NonNls String getNamespaceByPrefix(@NonNls String prefix);
  @Nullable String getPrefixByNamespace(@NonNls String namespace);
  String[] knownNamespaces();

  boolean hasNamespaceDeclarations();

  /**
   * @return map keys: prefixes values: namespaces
   */
  @NotNull Map<String, String> getLocalNamespaceDeclarations();

  @NotNull XmlTagValue getValue();

  @Nullable XmlNSDescriptor getNSDescriptor(@NonNls String namespace, boolean strict);

  boolean isEmpty();

  void collapseIfEmpty();

  @Nullable @NonNls
  String getSubTagText(@NonNls String qname);

  default boolean isCaseSensitive() {
    return true;
  }

  @Nullable
  default String getRealNs(@Nullable String value) {
    return value;
  }

  @Experimental
  @Nullable
  @Override
  default String getHostName() {
    return getLocalName();
  }
}
