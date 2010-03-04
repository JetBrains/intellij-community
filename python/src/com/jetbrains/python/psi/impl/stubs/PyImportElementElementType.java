package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyImportElementImpl;
import com.jetbrains.python.psi.stubs.PyImportElementStub;

import java.io.IOException;

/**
 * @author yole
 */
public class PyImportElementElementType extends PyStubElementType<PyImportElementStub, PyImportElement> {
  public PyImportElementElementType() {
    super("IMPORT_ELEMENT");
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
    final PyReferenceExpression importReference = psi.getImportReference();
    final PyTargetExpression asName = psi.getAsName();
    return new PyImportElementStubImpl(importReference != null ? importReference.getText() : "",
                                       asName != null ? asName.getText() : "",
                                       parentStub);
  }

  public void serialize(PyImportElementStub stub, StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getImportedName());
    dataStream.writeName(stub.getAsName());
  }

  public PyImportElementStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
    StringRef importedName = dataStream.readName();
    StringRef asName = dataStream.readName();
    return new PyImportElementStubImpl(importedName.getString(), asName.getString(), parentStub);
  }
}
