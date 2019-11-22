// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.xml.stub;

import com.intellij.psi.impl.source.xml.XmlStubBasedAttribute;
import com.intellij.psi.stubs.*;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

import static com.intellij.util.ObjectUtils.notNull;

public class XmlAttributeStubImpl extends StubBase<XmlStubBasedAttribute> implements XmlAttributeStub<XmlStubBasedAttribute> {

  @NotNull private final StringRef name;
  @Nullable private final StringRef value;

  XmlAttributeStubImpl(@Nullable StubElement<?> parent,
                       @NotNull StubInputStream dataStream,
                       @NotNull IStubElementType<? extends XmlAttributeStubImpl, ? extends XmlStubBasedAttribute> elementType)
    throws IOException {
    super(parent, elementType);
    name = notNull(dataStream.readName(), () -> StringRef.fromString(""));
    value = dataStream.readName();
  }

  XmlAttributeStubImpl(@NotNull XmlStubBasedAttribute psi,
                       @Nullable StubElement<?> parent,
                       @NotNull IStubElementType<? extends XmlAttributeStubImpl, ? extends XmlStubBasedAttribute> elementType) {
    super(parent, elementType);
    name = StringRef.fromString(psi.getName());
    value = StringRef.fromString(psi.getValue());
  }

  void serialize(StubOutputStream stream) throws IOException {
    stream.writeName(StringRef.toString(name));
    stream.writeName(StringRef.toString(value));
  }

  @NotNull
  public String getName() {
    return StringRef.toString(name);
  }

  @Nullable
  public String getValue() {
    return StringRef.toString(value);
  }
}
