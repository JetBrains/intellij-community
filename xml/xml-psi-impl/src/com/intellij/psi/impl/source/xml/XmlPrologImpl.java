// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.xml.XmlDoctype;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlProlog;
import org.jetbrains.annotations.NotNull;

public class XmlPrologImpl extends XmlElementImpl implements XmlProlog, XmlElementType {
  public XmlPrologImpl() {
    super(XML_PROLOG);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof XmlElementVisitor) {
      ((XmlElementVisitor)visitor).visitXmlProlog(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public XmlDoctype getDoctype() {
    ASTNode child = getNode().findChildByType(XML_DOCTYPE);
    return child != null ? child.getPsi(XmlDoctype.class) : null;
  }
}
