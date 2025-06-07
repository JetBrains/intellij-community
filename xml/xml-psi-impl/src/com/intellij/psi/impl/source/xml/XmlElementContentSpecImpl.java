// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.xml;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.xml.XmlElementContentGroup;
import com.intellij.psi.xml.XmlElementContentSpec;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.xml.XmlElementType.XML_ELEMENT_CONTENT_GROUP;
import static com.intellij.psi.xml.XmlElementType.XML_ELEMENT_CONTENT_SPEC;
import static com.intellij.psi.xml.XmlTokenType.*;

public class XmlElementContentSpecImpl extends XmlElementImpl implements XmlElementContentSpec {
  private static final Logger LOG = Logger.getInstance(XmlElementContentSpecImpl.class);

  public XmlElementContentSpecImpl() {
    super(XML_ELEMENT_CONTENT_SPEC);
  }

  @Override
  public boolean isEmpty() {
    return findElementByTokenType(XML_CONTENT_EMPTY) != null;
  }

  @Override
  public boolean isAny() {
    return findElementByTokenType(XML_CONTENT_ANY) != null;
  }

  @Override
  public boolean isMixed() {
    XmlElementContentGroup topGroup = getTopGroup();
    return topGroup != null && ((XmlElementImpl)topGroup).findElementByTokenType(XML_PCDATA) != null;
  }

  @Override
  public boolean hasChildren() {
    return !(isEmpty() || isAny() || isMixed());
  }

  @Override
  public XmlElementContentGroup getTopGroup() {
    return (XmlElementContentGroup)findElementByTokenType(XML_ELEMENT_CONTENT_GROUP);
  }

  @Override
  public PsiReference @NotNull [] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this);
  }

  @Override
  public void accept(final @NotNull PsiElementVisitor visitor) {
    if (visitor instanceof XmlElementVisitor) {
      ((XmlElementVisitor)visitor).visitXmlElement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }
}
