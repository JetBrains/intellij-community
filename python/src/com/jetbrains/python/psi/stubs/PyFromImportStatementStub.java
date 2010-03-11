package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.impl.PyQualifiedName;

/**
 * @author yole
 */
public interface PyFromImportStatementStub extends StubElement<PyFromImportStatement> {
  PyQualifiedName getImportSourceQName();
  boolean isStarImport();
  int getRelativeLevel();
}
