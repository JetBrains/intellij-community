// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.xml.stub;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.psi.impl.source.xml.XmlStubBasedAttribute;
import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.xml.IXmlAttributeElementType;
import org.jetbrains.annotations.NotNull;

public class XmlStubBasedAttributeElementType
  extends XmlStubBasedElementType<XmlStubBasedAttribute> implements ICompositeElementType, IXmlAttributeElementType {


  public XmlStubBasedAttributeElementType(@NotNull String debugName,
                                          @NotNull Language language) {
    super(debugName, language);
  }

  @Override
  public @NotNull XmlStubBasedAttribute createPsi(@NotNull ASTNode node) {
    return new XmlStubBasedAttribute(node);
  }
}
