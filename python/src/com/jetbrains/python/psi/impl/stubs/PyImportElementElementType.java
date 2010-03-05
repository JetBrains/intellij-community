package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyImportElementImpl;
import com.jetbrains.python.psi.stubs.PyImportElementStub;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
    final PyTargetExpression asName = psi.getAsNameElement();
    return new PyImportElementStubImpl(psi.getImportedQName(),
                                       asName != null ? asName.getName() : "",
                                       parentStub);
  }

  public void serialize(PyImportElementStub stub, StubOutputStream dataStream) throws IOException {
    final List<String> qName = stub.getImportedQName();
    if (qName == null) {
      dataStream.writeVarInt(0);
    }
    else {
      dataStream.writeVarInt(qName.size());
      for (String s : qName) {
        dataStream.writeName(s);
      }
    }
    dataStream.writeName(stub.getAsName());
  }

  public PyImportElementStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
    int size = dataStream.readVarInt();
    List<String> qName;
    if (size == 0) {
      qName = null;
    }
    else {
      qName = new ArrayList<String>(size);
      for (int i = 0; i < size; i++) {
        qName.add(dataStream.readName().getString());
      }
    }
    StringRef asName = dataStream.readName();
    return new PyImportElementStubImpl(qName, asName.getString(), parentStub);  }
}
