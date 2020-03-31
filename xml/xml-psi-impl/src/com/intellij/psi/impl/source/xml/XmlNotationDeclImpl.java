// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.xml.*;
import org.jetbrains.annotations.NotNull;

public class XmlNotationDeclImpl extends XmlElementImpl implements XmlNotationDecl, XmlElementType {
  private static final Logger LOG = Logger.getInstance(XmlElementDeclImpl.class);

  public XmlNotationDeclImpl() {
    super(XML_NOTATION_DECL);
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (child.getElementType() == XML_ELEMENT_CONTENT_SPEC) {
      return XmlChildRole.XML_ELEMENT_CONTENT_SPEC;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public XmlElement getNameElement() {
    return (XmlElement)findChildByRoleAsPsiElement(XmlChildRole.XML_NAME);
  }

  @Override
  public XmlElementContentSpec getContentSpecElement() {
    return (XmlElementContentSpec)findChildByRoleAsPsiElement(XmlChildRole.XML_ELEMENT_CONTENT_SPEC);
  }
}
