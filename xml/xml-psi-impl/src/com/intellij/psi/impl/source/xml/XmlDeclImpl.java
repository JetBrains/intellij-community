// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.xml;

import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.xml.XmlDecl;
import com.intellij.psi.xml.XmlElementType;
import org.jetbrains.annotations.NotNull;

public class XmlDeclImpl extends XmlElementImpl implements XmlDecl{
  public XmlDeclImpl() {
    super(XmlElementType.XML_DECL);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof XmlElementVisitor) {
      ((XmlElementVisitor)visitor).visitXmlDecl(this);
    }
    else {
      visitor.visitElement(this);
    }
  }
}
