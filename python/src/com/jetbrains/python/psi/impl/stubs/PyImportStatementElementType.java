package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyImportStatementImpl;
import com.jetbrains.python.psi.stubs.PyImportStatementStub;

import java.io.IOException;

/**
 * @author yole
 */
public class PyImportStatementElementType extends PyStubElementType<PyImportStatementStub, PyImportStatement> {
  public PyImportStatementElementType() {
    this("IMPORT_STATEMENT");
  }

  public PyImportStatementElementType(String debugName) {
    super(debugName);
  }

  @Override
  public PsiElement createElement(ASTNode node) {
    return new PyImportStatementImpl(node);
  }

  @Override
  public PyImportStatement createPsi(PyImportStatementStub stub) {
    return new PyImportStatementImpl(stub);
  }

  @Override
  public PyImportStatementStub createStub(PyImportStatement psi, StubElement parentStub) {
    return new PyImportStatementStubImpl(parentStub, getStubElementType());
  }

  public void serialize(PyImportStatementStub stub, StubOutputStream dataStream) throws IOException {
  }

  public PyImportStatementStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new PyImportStatementStubImpl(parentStub, getStubElementType());
  }

  protected IStubElementType getStubElementType() {
    return PyElementTypes.IMPORT_STATEMENT;
  }
}