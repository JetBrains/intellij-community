package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyImportElement;

/**
 * @author yole
 */
public interface PyImportElementStub extends StubElement<PyImportElement> {
  String getImportedName();
  String getAsName();
}
