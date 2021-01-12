// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

public class XmlElementDeclImpl extends XmlElementImpl implements XmlElementDecl, XmlElementType {
  public XmlElementDeclImpl() {
    super(XML_ELEMENT_DECL);
  }

  @Override
  public int getTextOffset() {
    final XmlElement name = getNameElement();
    return name != null ? name.getTextOffset() : super.getTextOffset();
  }

  @Override
  public XmlElement getNameElement() {
    ASTNode child = getNode().findChildByType(XML_NAME);
    return child != null ? child.getPsi(XmlElement.class) : null;
  }

  @Override
  public XmlElementContentSpec getContentSpecElement() {
    ASTNode child = getNode().findChildByType(XML_ELEMENT_CONTENT_SPEC);
    return child != null ? child.getPsi(XmlElementContentSpec.class) : null;
  }

  @Override
  public PsiMetaData getMetaData() {
    return MetaRegistry.getMeta(this);
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    XmlElementChangeUtil.doNameReplacement(this, getNameElement(), name);

    return null;
  }

  @Override
  public PsiReference @NotNull [] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this);
  }

  @Override
  public PsiElement getOriginalElement() {
    if (isPhysical()) return super.getOriginalElement();

    final PsiNamedElement element = XmlUtil.findRealNamedElement(this);

    if (element != null) {
      return element;
    }

    return this;
  }

  @Override
  public boolean canNavigate() {
    if (!isPhysical()) {
      return getOriginalElement() != this;
    }

    return super.canNavigate();
  }

  @Override
  public void navigate(boolean requestFocus) {
    if (!isPhysical()) {
      PsiElement element = getOriginalElement();

      if (element != this) {
        ((Navigatable)element).navigate(requestFocus);
        return;
      }
    }

    super.navigate(requestFocus);
  }

  @Override
  public String getName() {
    XmlElement xmlElement = getNameElement();
    if (xmlElement != null) return xmlElement.getText();

    return getNameFromEntityRef(this, XmlTokenType.XML_ELEMENT_DECL_START);
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    if (!(another instanceof XmlElementDecl)) return false;
    PsiElement element1 = this;
    PsiElement element2 = another;
    if (!element1.isPhysical()) element1 = element1.getOriginalElement();
    if (!element2.isPhysical()) element2 = element2.getOriginalElement();

    return element1 == element2;
  }

  @Override
  public PsiElement getNameIdentifier() {
    return null;
  }

  @Override
  @NotNull
  public PsiElement getNavigationElement() {
    return this;
  }
}
