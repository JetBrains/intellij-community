/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.xml.stubs;

import com.intellij.psi.stubs.*;
import com.intellij.util.xml.XmlFileHeader;

import java.io.IOException;

/**
 * @author Dmitry Avdeev
 *         Date: 8/8/12
 */
public class FileStubSerializer implements ObjectStubSerializer<FileStub, Stub> {

  public static FileStubSerializer INSTANCE = new FileStubSerializer();

  @Override
  public String getExternalId() {
    return "FileStubSerializer";
  }

  @Override
  public void serialize(FileStub stub, StubOutputStream dataStream) throws IOException {
    XmlFileHeader header = stub.getHeader();
    dataStream.writeName(header.getRootTagLocalName());
    dataStream.writeName(header.getRootTagNamespace());
    dataStream.writeName(header.getPublicId());
    dataStream.writeName(header.getSystemId());
  }

  @Override
  public FileStub deserialize(StubInputStream dataStream, Stub parentStub) throws IOException {
    return new FileStub(dataStream.readName(), dataStream.readName(), dataStream.readName(), dataStream.readName());
  }

  @Override
  public void indexStub(FileStub stub, IndexSink sink) {
  }

  @Override
  public String toString() {
    return "File";
  }
}
