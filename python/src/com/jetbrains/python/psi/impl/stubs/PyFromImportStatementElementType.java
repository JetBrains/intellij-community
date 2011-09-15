package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyFromImportStatementImpl;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.stubs.PyFromImportStatementStub;

import java.io.IOException;

/**
 * @author yole
 */
public class PyFromImportStatementElementType extends PyStubElementType<PyFromImportStatementStub, PyFromImportStatement> {
  public PyFromImportStatementElementType() {
    this("FROM_IMPORT_STATEMENT");
  }

  public PyFromImportStatementElementType(String debugName) {
    super(debugName);
  }

  @Override
  public PsiElement createElement(ASTNode node) {
    return new PyFromImportStatementImpl(node);
  }

  @Override
  public PyFromImportStatement createPsi(PyFromImportStatementStub stub) {
    return new PyFromImportStatementImpl(stub);
  }

  @Override
  public PyFromImportStatementStub createStub(PyFromImportStatement psi, StubElement parentStub) {
    return new PyFromImportStatementStubImpl(psi.getImportSourceQName(), psi.isStarImport(), psi.getRelativeLevel(), parentStub,
                                             getStubElementType());
  }

  public void serialize(PyFromImportStatementStub stub, StubOutputStream dataStream) throws IOException {
    final PyQualifiedName qName = stub.getImportSourceQName();
    PyQualifiedName.serialize(qName, dataStream);
    dataStream.writeBoolean(stub.isStarImport());
    dataStream.writeVarInt(stub.getRelativeLevel());
  }

  public PyFromImportStatementStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
    PyQualifiedName qName = PyQualifiedName.deserialize(dataStream);
    boolean isStarImport = dataStream.readBoolean();
    int relativeLevel = dataStream.readVarInt();
    return new PyFromImportStatementStubImpl(qName, isStarImport, relativeLevel, parentStub, getStubElementType());
  }

  protected IStubElementType getStubElementType() {
    return PyElementTypes.FROM_IMPORT_STATEMENT;
  }
}
