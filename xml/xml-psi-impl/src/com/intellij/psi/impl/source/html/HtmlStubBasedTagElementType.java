// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.html;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.psi.impl.source.xml.stub.XmlStubBasedElementType;
import com.intellij.psi.impl.source.xml.stub.XmlTagStubImpl;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.xml.IXmlTagElementType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class HtmlStubBasedTagElementType
  extends XmlStubBasedElementType<XmlTagStubImpl, HtmlStubBasedTagImpl> implements ICompositeElementType, IXmlTagElementType {

  public HtmlStubBasedTagElementType(@NotNull String debugName,
                                     @NotNull Language language) {
    super(debugName, language);
  }

  @Override
  public void serialize(@NotNull XmlTagStubImpl stub, @NotNull StubOutputStream dataStream) throws IOException {
    stub.serialize(dataStream);
  }

  @NotNull
  @Override
  public XmlTagStubImpl deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new XmlTagStubImpl(parentStub, dataStream, this);
  }

  @Override
  @NotNull
  public HtmlStubBasedTagImpl createPsi(@NotNull XmlTagStubImpl stub) {
    return new HtmlStubBasedTagImpl(stub, this);
  }

  @Override
  @NotNull
  public HtmlStubBasedTagImpl createPsi(@NotNull ASTNode node) {
    return new HtmlStubBasedTagImpl(node);
  }

  @NotNull
  @Override
  public XmlTagStubImpl createStub(@NotNull HtmlStubBasedTagImpl psi, StubElement parentStub) {
    return new XmlTagStubImpl(psi, parentStub, this);
  }

}
