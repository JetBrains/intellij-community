// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.stubs;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.ObjectStubSerializer;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.xml.stubs.index.DomElementClassIndex;
import com.intellij.util.xml.stubs.index.DomNamespaceKeyIndex;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;

/**
 * @author Dmitry Avdeev
 */
public class ElementStubSerializer implements ObjectStubSerializer<ElementStub, ElementStub> {

  @Override
  public void serialize(@NotNull ElementStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    dataStream.writeName(stub.getNamespaceKey());
    dataStream.writeVarInt(stub.getIndex());
    dataStream.writeBoolean(stub.isCustom());
    dataStream.writeName(stub.getElementClass());
    dataStream.writeUTFFast(stub.getValue());
  }

  @Override
  public @NotNull ElementStub deserialize(@NotNull StubInputStream dataStream, ElementStub parentStub) throws IOException {
    return new ElementStub(parentStub,
                           Objects.requireNonNull(dataStream.readNameString()),
                           dataStream.readNameString(),
                           dataStream.readVarInt(),
                           dataStream.readBoolean(),
                           dataStream.readNameString(),
                           dataStream.readUTFFast());
  }

  @Override
  public void indexStub(@NotNull ElementStub stub, @NotNull IndexSink sink) {
    final String namespaceKey = stub.getNamespaceKey();
    if (StringUtil.isNotEmpty(namespaceKey)) {
      sink.occurrence(DomNamespaceKeyIndex.KEY, namespaceKey);
    }

    final String elementClass = stub.getElementClass();
    if (elementClass != null) {
      sink.occurrence(DomElementClassIndex.KEY, elementClass);
    }
  }

  @Override
  public @NotNull String getExternalId() {
    return "xml.ElementStubSerializer";
  }

  @Override
  public String toString() {
    return "Element";
  }
}
