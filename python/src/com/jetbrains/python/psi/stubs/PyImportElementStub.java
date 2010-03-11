package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.impl.PyQualifiedName;

/**
 * @author yole
 */
public interface PyImportElementStub extends StubElement<PyImportElement> {
  PyQualifiedName getImportedQName();
  String getAsName();
}
