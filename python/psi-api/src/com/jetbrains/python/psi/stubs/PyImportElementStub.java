package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyImportElement;
import com.intellij.psi.util.QualifiedName;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyImportElementStub extends StubElement<PyImportElement> {
  @Nullable
  QualifiedName getImportedQName();
  
  String getAsName();
}
