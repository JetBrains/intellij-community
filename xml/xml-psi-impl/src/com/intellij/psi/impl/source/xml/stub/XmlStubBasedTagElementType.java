// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.xml.stub;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.psi.impl.source.xml.XmlStubBasedTag;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.xml.IXmlTagElementType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class XmlStubBasedTagElementType
  extends XmlStubBasedElementType<XmlTagStubImpl, XmlStubBasedTag> implements ICompositeElementType, IXmlTagElementType {

  public XmlStubBasedTagElementType(@NotNull String debugName,
                                    @NotNull Language language) {
    super(debugName, language);
  }

  @Override
  public void serialize(@NotNull XmlTagStubImpl stub, @NotNull StubOutputStream dataStream) throws IOException {
    stub.serialize(dataStream);
  }

  @Override
  public @NotNull XmlTagStubImpl deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new XmlTagStubImpl(parentStub, dataStream, this);
  }

  @Override
  public @NotNull XmlStubBasedTag createPsi(@NotNull XmlTagStubImpl stub) {
    return new XmlStubBasedTag(stub, this);
  }

  @Override
  public @NotNull XmlStubBasedTag createPsi(@NotNull ASTNode node) {
    return new XmlStubBasedTag(node);
  }

  @Override
  public @NotNull XmlTagStubImpl createStub(@NotNull XmlStubBasedTag psi, StubElement parentStub) {
    return new XmlTagStubImpl(psi, parentStub, this);
  }

}
