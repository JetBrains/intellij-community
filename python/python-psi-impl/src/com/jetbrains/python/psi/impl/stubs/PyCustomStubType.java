// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.stubs.PyClassStub;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

@ApiStatus.Internal
public interface PyCustomStubType<Psi extends PyElement, Stub extends PyCustomStub> {

  /**
   * @param psi element to create stub for
   * @return custom stub for the given psi or null.
   */
  @Nullable
  Stub createStub(@NotNull Psi psi);

  /**
   * @param stream stream containing serialized stub
   * @return a custom stub instance or null if it could not be read.
   * @see PyCustomStub#serialize(StubOutputStream)
   */
  @Nullable
  Stub deserializeStub(@NotNull StubInputStream stream) throws IOException;

  default void indexStub(@NotNull PyClassStub stub, @NotNull IndexSink sink) { }
}
