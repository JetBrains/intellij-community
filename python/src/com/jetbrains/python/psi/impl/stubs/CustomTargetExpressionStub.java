package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author yole
 */
public interface CustomTargetExpressionStub {
  @NotNull
  Class<? extends CustomTargetExpressionStubType> getTypeClass();
  void serialize(StubOutputStream stream) throws IOException;
}
