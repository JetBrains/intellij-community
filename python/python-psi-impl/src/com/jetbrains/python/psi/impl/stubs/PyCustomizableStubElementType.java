// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.jetbrains.python.psi.PyElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

@ApiStatus.Internal
public interface PyCustomizableStubElementType<Psi extends PyElement, Stub extends PyCustomStub, StubType extends PyCustomStubType<Psi, ? extends Stub>> {
  @NotNull
  List<StubType> getExtensions();

  default @Nullable Stub createCustomStub(@NotNull Psi psi) {
    for (StubType type : getExtensions()) {
      final Stub stub = type.createStub(psi);
      if (stub != null) return stub;
    }

    return null;
  }

  default void serializeCustomStub(@Nullable Stub stub, @NotNull StubOutputStream stream) throws IOException {
    final boolean hasCustomStub = stub != null;
    stream.writeBoolean(hasCustomStub);

    if (hasCustomStub) {
      stream.writeName(stub.getTypeClass().getCanonicalName());
      PyCustomStubFormat.write(stub, stream);
    }
  }

  /**
   * Reads the custom stub that {@link #serializeCustomStub} wrote. Returns null when no extension has the type of the stub.
   * This occurs, for example, when the plugin of the type is off.
   */
  default @Nullable Stub deserializeCustomStub(@NotNull StubInputStream stream) throws IOException {
    if (stream.readBoolean()) {
      final String typeName = stream.readNameString();
      final PyCustomStubFormat.Body body = PyCustomStubFormat.read(stream);
      for (StubType type : getExtensions()) {
        if (type.getClass().getCanonicalName().equals(typeName)) {
          return body.read(type);
        }
      }
    }

    return null;
  }
}
