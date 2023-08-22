// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.psi.xml.XmlElementContentSpec;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlNotationDecl;

public class XmlNotationDeclImpl extends XmlElementImpl implements XmlNotationDecl, XmlElementType {
  public XmlNotationDeclImpl() {
    super(XML_NOTATION_DECL);
  }

  @Override
  public XmlElementContentSpec getContentSpecElement() {
    ASTNode child = getNode().findChildByType(XML_ELEMENT_CONTENT_SPEC);
    return child != null ? child.getPsi(XmlElementContentSpec.class) : null;
  }
}
