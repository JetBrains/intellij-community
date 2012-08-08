package com.jetbrains.python.psi.impl.stubs;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubInputStream;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author yole
 */
public abstract class CustomTargetExpressionStubType<T extends CustomTargetExpressionStub> {
  public static ExtensionPointName<CustomTargetExpressionStubType> EP_NAME = ExtensionPointName.create("Pythonid.customTargetExpressionStubType");

  @Nullable
  public abstract T createStub(PyTargetExpression psi);

  @Nullable
  public abstract T deserializeStub(StubInputStream stream) throws IOException;

  public void indexStub(PyTargetExpressionStub stub, IndexSink sink) {
  }
}
