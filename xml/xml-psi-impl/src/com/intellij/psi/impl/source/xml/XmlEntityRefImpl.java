// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.xml;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.XmlResolveReferenceSupport;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.xml.*;
import org.jetbrains.annotations.NotNull;

public class XmlEntityRefImpl extends XmlElementImpl implements XmlEntityRef {

  public XmlEntityRefImpl() {
    super(XmlElementType.XML_ENTITY_REF);
  }

  @Override
  public XmlEntityDecl resolve(PsiFile targetFile) {
    return ApplicationManager.getApplication().getService(XmlResolveReferenceSupport.class).resolveReference(this, targetFile);
  }

  @Override
  public XmlTag getParentTag() {
    final XmlElement parent = (XmlElement)getParent();
    if (parent instanceof XmlTag) return (XmlTag)parent;
    return null;
  }

  @Override
  public XmlTagChild getNextSiblingInTag() {
    PsiElement nextSibling = getNextSibling();
    if (nextSibling instanceof XmlTagChild) return (XmlTagChild)nextSibling;
    return null;
  }

  @Override
  public XmlTagChild getPrevSiblingInTag() {
    final PsiElement prevSibling = getPrevSibling();
    if (prevSibling instanceof XmlTagChild) return (XmlTagChild)prevSibling;
    return null;
  }

  @Override
  public PsiReference @NotNull [] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof XmlElementVisitor) {
      ((XmlElementVisitor)visitor).visitXmlElement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }
}
