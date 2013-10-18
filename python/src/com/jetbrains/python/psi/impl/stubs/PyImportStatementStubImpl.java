package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.stubs.PyImportStatementStub;

/**
 * @author yole
 */
public class PyImportStatementStubImpl extends StubBase<PyImportStatement> implements PyImportStatementStub {
  public PyImportStatementStubImpl(StubElement parentStub, IStubElementType elementType) {
    super(parentStub, elementType);
  }

  @Override
  public String toString() {
    return "PyImportStatementStub()";
  }
}
