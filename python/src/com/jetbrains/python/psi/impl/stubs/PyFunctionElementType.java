/*
 * @author max
 */
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.PersistentStringEnumerator;
import com.intellij.util.io.StringRef;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyFunctionImpl;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;
import com.jetbrains.python.psi.stubs.PyFunctionStub;

import java.io.IOException;

public class PyFunctionElementType extends PyStubElementType<PyFunctionStub, PyFunction> {
  public PyFunctionElementType() {
    super("FUNCTION_DECLARATION");
  }

  public PsiElement createElement(final ASTNode node) {
    return new PyFunctionImpl(node);
  }

  public PyFunction createPsi(final PyFunctionStub stub) {
    return new PyFunctionImpl(stub);
  }

  public PyFunctionStub createStub(final PyFunction psi, final StubElement parentStub) {
    return new PyFunctionStubImpl(psi.getName(), parentStub);
  }

  public void serialize(final PyFunctionStub stub, final StubOutputStream dataStream)
      throws IOException {
    dataStream.writeName(stub.getName());
  }

  public PyFunctionStub deserialize(final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    String name = StringRef.toString(dataStream.readName());
    return new PyFunctionStubImpl(name, parentStub);
  }

  public void indexStub(final PyFunctionStub stub, final IndexSink sink) {
    final String name = stub.getName();
    if (name != null) {
      sink.occurrence(PyFunctionNameIndex.KEY, name);
    }
  }
}