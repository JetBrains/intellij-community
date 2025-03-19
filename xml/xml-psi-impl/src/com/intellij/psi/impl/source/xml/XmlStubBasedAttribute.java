// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.source.xml.stub.XmlAttributeStubImpl;
import com.intellij.psi.stubs.IStubElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XmlStubBasedAttribute extends XmlStubBasedAttributeBase<XmlAttributeStubImpl> {

  public XmlStubBasedAttribute(@NotNull XmlAttributeStubImpl stub,
                               @NotNull IStubElementType<? extends XmlAttributeStubImpl, ? extends XmlStubBasedAttribute> nodeType) {
    super(stub, nodeType);
  }

  public XmlStubBasedAttribute(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public @NotNull String getName() {
    XmlAttributeStubImpl stub = getGreenStub();
    if (stub != null) {
      return stub.getName();
    }
    return super.getName();
  }

  @Override
  public @Nullable String getValue() {
    XmlAttributeStubImpl stub = getGreenStub();
    if (stub != null) {
      return stub.getValue();
    }
    return super.getValue();
  }
}
