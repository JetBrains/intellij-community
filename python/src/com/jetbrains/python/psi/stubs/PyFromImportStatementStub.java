package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyFromImportStatement;

/**
 * @author yole
 */
public interface PyFromImportStatementStub extends StubElement<PyFromImportStatement> {
  boolean isStarImport();
}
