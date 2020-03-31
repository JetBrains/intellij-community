// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.PlatformPrebuiltStubsProviderBase;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyFileElementType;
import org.jetbrains.annotations.NotNull;

public class PyPrebuiltStubsProvider extends PlatformPrebuiltStubsProviderBase {

  public static final String NAME = PythonFileType.INSTANCE.getName();

  @Override
  protected int getStubVersion() {
    return PyFileElementType.INSTANCE.getStubVersion();
  }

  @NotNull
  @Override
  protected String getDirName() {
    return NAME;
  }
}
