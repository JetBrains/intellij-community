// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.xml.stub;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.psi.impl.source.xml.XmlStubBasedAttribute;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.xml.IXmlAttributeElementType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class XmlStubBasedAttributeElementType
  extends XmlStubBasedElementType<XmlAttributeStubImpl, XmlStubBasedAttribute> implements ICompositeElementType, IXmlAttributeElementType {


  public XmlStubBasedAttributeElementType(@NotNull String debugName,
                                          @NotNull Language language) {
    super(debugName, language);
  }

  @Override
  public void serialize(@NotNull XmlAttributeStubImpl stub, @NotNull StubOutputStream dataStream) throws IOException {
    stub.serialize(dataStream);
  }

  @Override
  public @NotNull XmlAttributeStubImpl deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new XmlAttributeStubImpl(parentStub, dataStream, this);
  }

  @Override
  public @NotNull XmlStubBasedAttribute createPsi(@NotNull XmlAttributeStubImpl stub) {
    return new XmlStubBasedAttribute(stub, this);
  }

  @Override
  public @NotNull XmlStubBasedAttribute createPsi(@NotNull ASTNode node) {
    return new XmlStubBasedAttribute(node);
  }

  @Override
  public @NotNull XmlAttributeStubImpl createStub(@NotNull XmlStubBasedAttribute psi, StubElement parentStub) {
    return new XmlAttributeStubImpl(psi, parentStub, this);
  }

}
