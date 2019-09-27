// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.xml.stub;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.xml.XmlStubBasedTag;
import com.intellij.psi.stubs.*;
import com.intellij.psi.tree.ICompositeElementType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Locale;

public class XmlStubBasedTagElementType
  extends IStubElementType<XmlTagStubImpl, XmlStubBasedTag> implements ICompositeElementType {

  private final @NotNull String externalId;

  public XmlStubBasedTagElementType(@NotNull String debugName,
                                    @NotNull Language language) {
    super(language.getID().toUpperCase(Locale.ENGLISH) + ":" + debugName, language);
    externalId = language.getID().toUpperCase(Locale.ENGLISH) + ":" + debugName;
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
  public void indexStub(@NotNull XmlTagStubImpl stub, @NotNull IndexSink sink) {
  }

  @Override
  @NotNull
  public XmlStubBasedTag createPsi(@NotNull XmlTagStubImpl stub) {
    return new XmlStubBasedTag(stub, this);
  }

  @NotNull
  public XmlStubBasedTag createPsi(@NotNull ASTNode node) {
    return new XmlStubBasedTag(node);
  }

  @NotNull
  @Override
  public String getExternalId() {
    return externalId;
  }

  @NotNull
  @Override
  public XmlTagStubImpl createStub(@NotNull XmlStubBasedTag psi, StubElement parentStub) {
    return new XmlTagStubImpl(psi, parentStub, this);
  }

  @NotNull
  @Override
  public ASTNode createCompositeNode() {
    return new CompositeElement(this);
  }
}
