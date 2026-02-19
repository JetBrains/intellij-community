// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.util;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlAttributeDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IncludedXmlAttribute extends IncludedXmlElement<XmlAttribute> implements XmlAttribute {

  public IncludedXmlAttribute(@NotNull XmlAttribute original, @Nullable XmlTag parent) {
    super(original, parent);
  }

  @Override
  public @NonNls @NotNull String getName() {
    return getOriginal().getName();
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    throw new UnsupportedOperationException("Can't modify included elements");
  }

  @Override
  public @NonNls @NotNull String getLocalName() {
    return getOriginal().getLocalName();
  }

  @Override
  public XmlElement getNameElement() {
    return getOriginal().getNameElement();
  }

  @Override
  public @NonNls @NotNull String getNamespace() {
    return getOriginal().getNamespace();
  }

  @Override
  public @NonNls @NotNull String getNamespacePrefix() {
    return getOriginal().getNamespacePrefix();
  }

  @Override
  public XmlTag getParent() {
    return (XmlTag)super.getParent();
  }

  @Override
  public String getValue() {
    return getOriginal().getValue();
  }

  @Override
  public String getDisplayValue() {
    return getOriginal().getDisplayValue();
  }

  @Override
  public int physicalToDisplay(int offset) {
    return getOriginal().physicalToDisplay(offset);
  }

  @Override
  public int displayToPhysical(int offset) {
    return getOriginal().displayToPhysical(offset);
  }

  @Override
  public @NotNull TextRange getValueTextRange() {
    return getOriginal().getValueTextRange();
  }

  @Override
  public boolean isNamespaceDeclaration() {
    return getOriginal().isNamespaceDeclaration();
  }

  @Override
  public @Nullable XmlAttributeDescriptor getDescriptor() {
    return getOriginal().getDescriptor();
  }

  @Override
  public @Nullable XmlAttributeValue getValueElement() {
    return getOriginal().getValueElement();
  }

  @Override
  public void setValue(@NotNull String value) throws IncorrectOperationException {
    throw new UnsupportedOperationException("Can't modify included elements");
  }
}
