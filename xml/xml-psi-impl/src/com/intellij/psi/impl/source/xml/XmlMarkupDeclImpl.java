// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.xml;

import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlMarkupDecl;

public class XmlMarkupDeclImpl extends XmlElementImpl implements XmlMarkupDecl {
  public XmlMarkupDeclImpl() {
    super(XmlElementType.XML_MARKUP_DECL);
  }

  @Override
  public PsiMetaData getMetaData(){
    return MetaRegistry.getMeta(this);
  }

}
