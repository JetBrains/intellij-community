package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.stubs.PyImportStatementStub;

/**
 * @author yole
 */
public class PyImportStatementStubImpl extends StubBase<PyImportStatement> implements PyImportStatementStub {
  public PyImportStatementStubImpl(StubElement parentStub) {
    super(parentStub, PyElementTypes.IMPORT_STATEMENT);
  }
}
