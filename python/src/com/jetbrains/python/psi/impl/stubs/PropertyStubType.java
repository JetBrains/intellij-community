package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubInputStream;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.stubs.PropertyStubStorage;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author yole
 */
public class PropertyStubType extends CustomTargetExpressionStubType<PropertyStubStorage> {
  @Nullable
  @Override
  public PropertyStubStorage createStub(PyTargetExpression psi) {
    return PropertyStubStorage.fromCall(psi.findAssignedValue());
  }

  @Override
  public PropertyStubStorage deserializeStub(StubInputStream stream) throws IOException {
    return PropertyStubStorage.deserialize(stream);
  }
}
