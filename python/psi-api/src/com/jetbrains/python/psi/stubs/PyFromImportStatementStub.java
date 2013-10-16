package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.intellij.psi.util.QualifiedName;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyFromImportStatementStub extends StubElement<PyFromImportStatement> {
  @Nullable
  QualifiedName getImportSourceQName();
  boolean isStarImport();
  int getRelativeLevel();
}
