// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.stubs;

import com.intellij.psi.stubs.*;
import com.intellij.util.xml.XmlFileHeader;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;

/**
 * @author Dmitry Avdeev
 */
public class FileStubSerializer implements ObjectStubSerializer<FileStub, Stub> {

  @Override
  public @NotNull String getExternalId() {
    return "xml.FileStubSerializer";
  }

  @Override
  public void serialize(@NotNull FileStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    XmlFileHeader header = stub.getHeader();
    dataStream.writeName(header.getRootTagLocalName());
    dataStream.writeName(header.getRootTagNamespace());
    dataStream.writeName(header.getPublicId());
    dataStream.writeName(header.getSystemId());
  }

  @Override
  public @NotNull FileStub deserialize(@NotNull StubInputStream dataStream, Stub parentStub) throws IOException {
    return new FileStub(Objects.requireNonNull(dataStream.readNameString()),
                        dataStream.readNameString(), dataStream.readNameString(), dataStream.readNameString());
  }

  @Override
  public void indexStub(@NotNull FileStub stub, @NotNull IndexSink sink) {
  }

  @Override
  public String toString() {
    return "File";
  }
}
