// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.xml.stub;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.xml.XmlStubBasedAttribute;
import com.intellij.psi.stubs.*;
import com.intellij.psi.tree.ICompositeElementType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Locale;

public class XmlStubBasedAttributeElementType
  extends IStubElementType<XmlAttributeStubImpl, XmlStubBasedAttribute> implements ICompositeElementType {

  private final @NotNull String externalId;

  public XmlStubBasedAttributeElementType(@NotNull String debugName,
                                          @NotNull Language language) {
    super(language.getID().toUpperCase(Locale.ENGLISH) + ":" + debugName, language);
    externalId = language.getID().toUpperCase(Locale.ENGLISH) + ":" + debugName;
  }

  @Override
  public void serialize(@NotNull XmlAttributeStubImpl stub, @NotNull StubOutputStream dataStream) throws IOException {
    stub.serialize(dataStream);
  }

  @NotNull
  @Override
  public XmlAttributeStubImpl deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new XmlAttributeStubImpl(parentStub, dataStream, this);
  }

  @Override
  public void indexStub(@NotNull XmlAttributeStubImpl stub, @NotNull IndexSink sink) {
  }

  @Override
  @NotNull
  public XmlStubBasedAttribute createPsi(@NotNull XmlAttributeStubImpl stub) {
    return new XmlStubBasedAttribute(stub, this);
  }

  @NotNull
  public XmlStubBasedAttribute createPsi(@NotNull ASTNode node) {
    return new XmlStubBasedAttribute(node);
  }

  @NotNull
  @Override
  public String getExternalId() {
    return externalId;
  }

  @NotNull
  @Override
  public XmlAttributeStubImpl createStub(@NotNull XmlStubBasedAttribute psi, StubElement parentStub) {
    return new XmlAttributeStubImpl(psi, parentStub, this);
  }

  @NotNull
  @Override
  public ASTNode createCompositeNode() {
    return new CompositeElement(this);
  }
}
