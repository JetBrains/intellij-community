// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.stubs;

import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.ObjectStubSerializer;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;

/**
 * @author Dmitry Avdeev
 */
public class AttributeStubSerializer implements ObjectStubSerializer<AttributeStub, ElementStub> {

  @Override
  public @NotNull String getExternalId() {
    return "xml.AttributeStub";
  }

  @Override
  public void serialize(@NotNull AttributeStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    dataStream.writeName(stub.getNamespaceKey());
    dataStream.writeUTFFast(stub.getValue());
  }

  @Override
  public @NotNull AttributeStub deserialize(@NotNull StubInputStream dataStream, ElementStub parentStub) throws IOException {
    return new AttributeStub(parentStub,
                             Objects.requireNonNull(dataStream.readNameString()),
                             dataStream.readNameString(), dataStream.readUTFFast());
  }

  @Override
  public void indexStub(@NotNull AttributeStub stub, @NotNull IndexSink sink) {
  }

  @Override
  public String toString() {
    return "Attribute";
  }
}
