// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.xml.stub;

import com.intellij.psi.impl.source.xml.XmlStubBasedTag;
import com.intellij.psi.stubs.*;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

import static com.intellij.util.ObjectUtils.notNull;

public class XmlTagStubImpl extends StubBase<XmlStubBasedTag> implements XmlTagStub<XmlStubBasedTag> {

  private final @NotNull String name;

  public XmlTagStubImpl(@Nullable StubElement<?> parent,
                 @NotNull StubInputStream dataStream,
                 @NotNull IStubElementType<? extends XmlTagStubImpl, ? extends XmlStubBasedTag> elementType)
    throws IOException {
    super(parent, elementType);
    name = notNull(StringRef.toString(dataStream.readName()), "");
  }

  public XmlTagStubImpl(@NotNull XmlStubBasedTag psi,
                 @Nullable StubElement<?> parent,
                 @NotNull IStubElementType<? extends XmlTagStubImpl, ? extends XmlStubBasedTag> elementType) {
    super(parent, elementType);
    name = psi.getName();
  }

  public void serialize(StubOutputStream stream) throws IOException {
    stream.writeName(name);
  }

  @Override
  public @NotNull String getName() {
    return name;
  }

}
