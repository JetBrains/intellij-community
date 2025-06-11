// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.html;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.psi.impl.source.xml.stub.XmlStubBasedElementType;
import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.xml.IXmlTagElementType;
import org.jetbrains.annotations.NotNull;

public class HtmlStubBasedTagElementType
  extends XmlStubBasedElementType<HtmlStubBasedTagImpl> implements ICompositeElementType, IXmlTagElementType {

  public HtmlStubBasedTagElementType(@NotNull String debugName,
                                     @NotNull Language language) {
    super(debugName, language);
  }

  @Override
  public @NotNull HtmlStubBasedTagImpl createPsi(@NotNull ASTNode node) {
    return new HtmlStubBasedTagImpl(node);
  }
}
