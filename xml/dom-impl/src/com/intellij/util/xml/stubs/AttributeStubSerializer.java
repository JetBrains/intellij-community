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

import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.ObjectStubSerializer;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author Dmitry Avdeev
 */
public class AttributeStubSerializer implements ObjectStubSerializer<AttributeStub, ElementStub> {

  final static ObjectStubSerializer INSTANCE = new AttributeStubSerializer();

  @NotNull
  @Override
  public String getExternalId() {
    return "AttributeStub";
  }

  @Override
  public void serialize(@NotNull AttributeStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    dataStream.writeName(stub.getNamespaceKey());
    dataStream.writeUTFFast(stub.getValue() == null ? "" : stub.getValue());
  }

  @NotNull
  @Override
  public AttributeStub deserialize(@NotNull StubInputStream dataStream, ElementStub parentStub) throws IOException {
    return new AttributeStub(parentStub, dataStream.readName(), dataStream.readName(), dataStream.readUTFFast());
  }

  @Override
  public void indexStub(@NotNull AttributeStub stub, @NotNull IndexSink sink) {
  }

  @Override
  public String toString() {
    return "Attribute";
  }
}
