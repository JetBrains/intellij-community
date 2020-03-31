// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.xml.stub;

import com.intellij.psi.impl.source.xml.XmlStubBasedTag;
import com.intellij.psi.stubs.*;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

import static com.intellij.util.ObjectUtils.notNull;

public class XmlTagStubImpl extends StubBase<XmlStubBasedTag> implements XmlTagStub<XmlStubBasedTag> {

  @NotNull private final String name;

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

  @NotNull
  public String getName() {
    return name;
  }

}
