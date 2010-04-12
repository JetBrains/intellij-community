package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyImportElementStub extends StubElement<PyImportElement> {
  @Nullable
  PyQualifiedName getImportedQName();
  
  String getAsName();
}
