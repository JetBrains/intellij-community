// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  @Override
  public String getName() {
    XmlAttributeStubImpl stub = getGreenStub();
    if (stub != null) {
      return stub.getName();
    }
    return super.getName();
  }

  @Nullable
  @Override
  public String getValue() {
    XmlAttributeStubImpl stub = getGreenStub();
    if (stub != null) {
      return stub.getValue();
    }
    return super.getValue();
  }
}
