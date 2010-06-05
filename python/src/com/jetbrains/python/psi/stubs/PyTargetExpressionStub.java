package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.NamedStub;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyTargetExpressionStub extends NamedStub<PyTargetExpression> {
  @Nullable
  PyQualifiedName getInitializer();

  /**
   * @return a pack of names assigned in a property() call if this target defines a property, or null.
   */
  @Nullable
  PropertyStubStorage getPropertyPack();
}
