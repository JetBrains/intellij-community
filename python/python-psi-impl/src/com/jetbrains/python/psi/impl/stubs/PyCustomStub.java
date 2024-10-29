// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public interface PyCustomStub {

  /**
   * @return type class to distinguish one custom stub from another.
   */
  @NotNull
  Class<? extends PyCustomStubType<?, ?>> getTypeClass();

  /**
   * @param stream stream to serialize {@code this} stub
   * @see PyCustomStubType#deserializeStub(StubInputStream)
   */
  void serialize(@NotNull StubOutputStream stream) throws IOException;
}
