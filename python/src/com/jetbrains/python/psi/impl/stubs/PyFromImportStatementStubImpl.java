package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.stubs.PyFromImportStatementStub;

/**
 * @author yole
 */
public class PyFromImportStatementStubImpl extends StubBase<PyFromImportStatement> implements PyFromImportStatementStub {
  private final PyQualifiedName myImportSourceQName;
  private final boolean myStarImport;
  private final int myRelativeLevel;

  public PyFromImportStatementStubImpl(PyQualifiedName importSourceQName, boolean isStarImport, int relativeLevel,
                                       final StubElement parent, IStubElementType elementType) {
    super(parent, elementType);
    myImportSourceQName = importSourceQName;
    myStarImport = isStarImport;
    myRelativeLevel = relativeLevel;
  }

  public PyQualifiedName getImportSourceQName() {
    return myImportSourceQName;
  }

  public boolean isStarImport() {
    return myStarImport;
  }

  public int getRelativeLevel() {
    return myRelativeLevel;
  }

  @Override
  public String toString() {
    return "PyFromImportStatementStub(source=" + myImportSourceQName + " starImport=" + myStarImport + ")";
  }
}
