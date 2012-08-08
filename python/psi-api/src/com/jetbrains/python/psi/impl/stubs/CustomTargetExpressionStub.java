package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubOutputStream;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author yole
 */
public interface CustomTargetExpressionStub {
  @NotNull
  Class<? extends CustomTargetExpressionStubType> getTypeClass();
  void serialize(StubOutputStream stream) throws IOException;

  @Nullable
  PyQualifiedName getCalleeName();
}
