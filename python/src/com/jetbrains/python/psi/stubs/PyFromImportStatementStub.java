package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyFromImportStatement;

import java.util.List;

/**
 * @author yole
 */
public interface PyFromImportStatementStub extends StubElement<PyFromImportStatement> {
  List<String> getImportSourceQName();
  boolean isStarImport();
  int getRelativeLevel();
}
