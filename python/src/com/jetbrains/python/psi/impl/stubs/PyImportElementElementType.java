package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyImportElementImpl;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.stubs.PyImportElementStub;

import java.io.IOException;

/**
 * @author yole
 */
public class PyImportElementElementType extends PyStubElementType<PyImportElementStub, PyImportElement> {
  public PyImportElementElementType() {
    this("IMPORT_ELEMENT");
  }

  public PyImportElementElementType(String debugName) {
    super(debugName);
  }

  @Override
  public PsiElement createElement(ASTNode node) {
    return new PyImportElementImpl(node);
  }

  @Override
  public PyImportElement createPsi(PyImportElementStub stub) {
    return new PyImportElementImpl(stub);
  }

  @Override
  public PyImportElementStub createStub(PyImportElement psi, StubElement parentStub) {
    final PyTargetExpression asName = psi.getAsNameElement();
    return new PyImportElementStubImpl(psi.getImportedQName(), asName != null ? asName.getName() : "", parentStub, getStubElementType());
  }

  public void serialize(PyImportElementStub stub, StubOutputStream dataStream) throws IOException {
    PyQualifiedName.serialize(stub.getImportedQName(), dataStream);
    dataStream.writeName(stub.getAsName());
  }

  public PyImportElementStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
    PyQualifiedName qName = PyQualifiedName.deserialize(dataStream);
    StringRef asName = dataStream.readName();
    return new PyImportElementStubImpl(qName, asName.getString(), parentStub, getStubElementType());
  }

  protected IStubElementType getStubElementType() {
    return PyElementTypes.IMPORT_ELEMENT;
  }
}
