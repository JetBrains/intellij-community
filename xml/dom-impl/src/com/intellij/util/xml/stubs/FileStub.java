// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.stubs;

import com.intellij.psi.stubs.ObjectStubSerializer;
import com.intellij.psi.stubs.Stub;
import com.intellij.util.xml.XmlFileHeader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public final class FileStub extends ElementStub {
  private final XmlFileHeader myHeader;

  FileStub(@NotNull String tagName, @Nullable String tagNamespace, @Nullable String publicId, @Nullable String systemId) {
    super(null, tagName, tagNamespace, 0, false, null, "");
    myHeader = new XmlFileHeader(tagName, tagNamespace, publicId, systemId);
  }

  public FileStub(XmlFileHeader header) {
    super(null, header.getRootTagLocalName(), header.getRootTagNamespace(), 0, false, null, "");
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
  public ObjectStubSerializer<?, Stub> getStubType() {
    return DomElementTypeHolder.FileStubSerializer;
  }
}
