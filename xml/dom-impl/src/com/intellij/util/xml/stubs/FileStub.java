/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.psi.stubs.ObjectStubSerializer;
import com.intellij.psi.stubs.Stub;
import com.intellij.util.io.StringRef;
import com.intellij.util.xml.XmlFileHeader;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class FileStub extends ElementStub {

  private final XmlFileHeader myHeader;

  public FileStub(StringRef tagName, StringRef tagNamespace, StringRef publicId, StringRef systemId) {
    super(null, tagName, tagNamespace, 0, false, null, "");
    myHeader = new XmlFileHeader(tagName.getString(),
                                 tagNamespace == null ? null : tagNamespace.getString(),
                                 publicId == null ? null : publicId.getString(),
                                 systemId == null ? null : systemId.getString());
  }

  public FileStub(XmlFileHeader header) {
    super(null, StringRef.fromString(header.getRootTagLocalName()), StringRef.fromString(header.getRootTagNamespace()), 0, false, null, "");
    myHeader = header;
  }

  public XmlFileHeader getHeader() {
    return myHeader;
  }

  @Nullable
  public ElementStub getRootTagStub() {
    List<? extends Stub> stubs = getChildrenStubs();
    return stubs.isEmpty() ? null : (ElementStub)stubs.get(0);
  }

  @Override
  public ObjectStubSerializer getStubType() {
    return FileStubSerializer.INSTANCE;
  }
}
