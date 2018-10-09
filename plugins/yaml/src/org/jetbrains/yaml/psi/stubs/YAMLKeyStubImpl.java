// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.psi.stubs;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLKeyValue;

public class YAMLKeyStubImpl extends StubBase<YAMLKeyValue> implements YAMLKeyStub {
  @NotNull  private final StringRef myKeyText;
  @NotNull  private final StringRef myKeyPath;

  public YAMLKeyStubImpl(StubElement parent, @NotNull IStubElementType elementType,
                         @NotNull StringRef keyText, @NotNull StringRef keyPath) {
    super(parent, elementType);
    myKeyText = keyText;
    myKeyPath = keyPath;
  }

  public YAMLKeyStubImpl(final StubElement parent, @NotNull IStubElementType elementType,
                         @NotNull String keyText, @NotNull String keyPath) {
    this(parent, elementType, StringRef.fromString(keyText), StringRef.fromString(keyPath));
  }

  @Override
  @NotNull
  public String getKeyText() {
    return myKeyText.getString();
  }

  @Override
  @NotNull
  public String getKeyPath() {
    return myKeyPath.getString();
  }
}
