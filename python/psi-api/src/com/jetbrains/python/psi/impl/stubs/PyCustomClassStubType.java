// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public abstract class PyCustomClassStubType<T extends PyCustomClassStub> {

  public static final ExtensionPointName<PyCustomClassStubType> EP_NAME = ExtensionPointName.create("Pythonid.customClassStubType");

  /**
   * @param psi class to create stub for
   * @return custom stub for given class or null.
   */
  @Nullable
  public abstract T createStub(@NotNull PyClass psi);

  /**
   * @param stream stream containing serialized stub
   * @return a custom stub instance or null if it could not be read.
   * @throws IOException
   * @see PyCustomClassStub#serialize(StubOutputStream)
   */
  @Nullable
  public abstract T deserializeStub(@NotNull StubInputStream stream) throws IOException;
}
