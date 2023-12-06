// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.xml.stub;

import com.intellij.psi.impl.source.xml.XmlStubBasedAttribute;
import com.intellij.psi.stubs.*;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

import static com.intellij.util.ObjectUtils.notNull;

public class XmlAttributeStubImpl extends StubBase<XmlStubBasedAttribute> implements XmlAttributeStub<XmlStubBasedAttribute> {

  private final @NotNull String name;
  private final @Nullable String value;

  public XmlAttributeStubImpl(@Nullable StubElement<?> parent,
                       @NotNull StubInputStream dataStream,
                       @NotNull IStubElementType<? extends XmlAttributeStubImpl, ? extends XmlStubBasedAttribute> elementType)
    throws IOException {
    super(parent, elementType);
    name = notNull(StringRef.toString(dataStream.readName()), "");
    value = StringRef.toString(dataStream.readName());
  }

  public XmlAttributeStubImpl(@NotNull XmlStubBasedAttribute psi,
                       @Nullable StubElement<?> parent,
                       @NotNull IStubElementType<? extends XmlAttributeStubImpl, ? extends XmlStubBasedAttribute> elementType) {
    super(parent, elementType);
    name = psi.getName();
    value = psi.getValue();
  }

  void serialize(StubOutputStream stream) throws IOException {
    stream.writeName(name);
    stream.writeName(value);
  }

  public @NotNull String getName() {
    return name;
  }

  public @Nullable String getValue() {
    return value;
  }
}
