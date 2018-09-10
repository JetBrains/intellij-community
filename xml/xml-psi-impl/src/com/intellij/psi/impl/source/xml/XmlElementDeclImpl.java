/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mike
 */
public class XmlElementDeclImpl extends XmlElementImpl implements XmlElementDecl, XmlElementType {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.XmlElementDeclImpl");

  public XmlElementDeclImpl() {
    super(XML_ELEMENT_DECL);
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == XML_NAME) {
      return XmlChildRole.XML_NAME;
    }
    else if (i == XML_ELEMENT_CONTENT_SPEC) {
      return XmlChildRole.XML_ELEMENT_CONTENT_SPEC;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public int getTextOffset() {
    final XmlElement name = getNameElement();
    return name != null ? name.getTextOffset() : super.getTextOffset();
  }

  @Override
  public XmlElement getNameElement() {
    return (XmlElement)findChildByRoleAsPsiElement(XmlChildRole.XML_NAME);
  }

  @Override
  public XmlElementContentSpec getContentSpecElement() {
    return (XmlElementContentSpec)findChildByRoleAsPsiElement(XmlChildRole.XML_ELEMENT_CONTENT_SPEC);
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
  @NotNull
  public PsiReference[] getReferences() {
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
