// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.source.xml.stub.XmlTagStubImpl;
import com.intellij.psi.stubs.IStubElementType;
import org.jetbrains.annotations.NotNull;

public class XmlStubBasedTag extends XmlStubBasedTagBase<XmlTagStubImpl> {

  public XmlStubBasedTag(@NotNull XmlTagStubImpl stub,
                         @NotNull IStubElementType<? extends XmlTagStubImpl, ? extends XmlStubBasedTag> nodeType) {
    super(stub, nodeType);
  }

  public XmlStubBasedTag(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  @Override
  public String getName() {
    XmlTagStubImpl stub = getGreenStub();
    if (stub != null) {
      return stub.getName();
    }
    return super.getName();
  }
}
