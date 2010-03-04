package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.stubs.PyFromImportStatementStub;

/**
 * @author yole
 */
public class PyFromImportStatementStubImpl extends StubBase<PyFromImportStatement> implements PyFromImportStatementStub {
  private final boolean myStarImport;
  private final int myRelativeLevel;

  public PyFromImportStatementStubImpl(boolean isStarImport, int relativeLevel, final StubElement parent) {
    super(parent, PyElementTypes.FROM_IMPORT_STATEMENT);
    myStarImport = isStarImport;
    myRelativeLevel = relativeLevel;
  }

  public boolean isStarImport() {
    return myStarImport;
  }

  public int getRelativeLevel() {
    return myRelativeLevel;
  }
}
