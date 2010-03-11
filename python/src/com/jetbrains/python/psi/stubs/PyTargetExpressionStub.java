package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.NamedStub;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyQualifiedName;

/**
 * @author yole
 */
public interface PyTargetExpressionStub extends NamedStub<PyTargetExpression> {
  PyQualifiedName getInitializer();
}
