/*
 * @author max
 */
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.PersistentStringEnumerator;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyClassImpl;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyClassStub;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class PyClassElementType extends PyStubElementType<PyClassStub, PyClass> {
  public PyClassElementType() {
    super("CLASS_DECLARATION");
  }

  public PsiElement createElement(final ASTNode node) {
    return new PyClassImpl(node);
  }

  public PyClass createPsi(final PyClassStub stub) {
    return new PyClassImpl(stub);
  }

  public PyClassStub createStub(final PyClass psi, final StubElement parentStub) {
    return new PyClassStubImpl(psi.getName(), parentStub);
  }

  public void serialize(final PyClassStub pyClassStub, final DataOutputStream dataStream,
                        final PersistentStringEnumerator nameStorage) throws IOException {
    DataInputOutputUtil.writeNAME(dataStream, pyClassStub.getName(), nameStorage);
  }

  public PyClassStub deserialize(final DataInputStream dataStream, final StubElement parentStub,
                                 final PersistentStringEnumerator nameStorage) throws IOException {
    String name = DataInputOutputUtil.readNAME(dataStream, nameStorage);
    return new PyClassStubImpl(name, parentStub);
  }

  public void indexStub(final PyClassStub stub, final IndexSink sink) {
    final String name = stub.getName();
    if (name != null) {
      sink.occurence(PyClassNameIndex.KEY, name);
    }
  }
}