package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyFromImportStatementStub extends StubElement<PyFromImportStatement> {
  @Nullable
  PyQualifiedName getImportSourceQName();
  boolean isStarImport();
  int getRelativeLevel();
}
