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
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.impl.PyClassImpl;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.stubs.PySuperClassIndex;

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
    final PyExpression[] exprs = psi.getSuperClassExpressions();
    String[] superClasses = new String[exprs.length];
    for(int i=0; i<exprs.length; i++) {
      final PyExpression expression = exprs[i];
      if (expression instanceof PyReferenceExpression) {
        superClasses [i] = ((PyReferenceExpression) expression).getReferencedName();
      }
      else {
        superClasses [i] = expression.getText();
      }
    }
    return new PyClassStubImpl(psi.getName(), parentStub, superClasses);
  }

  public void serialize(final PyClassStub pyClassStub, final DataOutputStream dataStream,
                        final PersistentStringEnumerator nameStorage) throws IOException {
    DataInputOutputUtil.writeNAME(dataStream, pyClassStub.getName(), nameStorage);
    final String[] classes = pyClassStub.getSuperClasses();
    dataStream.writeByte(classes.length);
    for(String s: classes) {
      DataInputOutputUtil.writeNAME(dataStream, s, nameStorage);
    }
  }

  public PyClassStub deserialize(final DataInputStream dataStream, final StubElement parentStub,
                                 final PersistentStringEnumerator nameStorage) throws IOException {
    String name = DataInputOutputUtil.readNAME(dataStream, nameStorage);
    int superClassCount = dataStream.readByte();
    String[] superClasses = new String[superClassCount];
    for(int i=0; i<superClassCount; i++) {
      superClasses [i] = DataInputOutputUtil.readNAME(dataStream, nameStorage);
    }
    return new PyClassStubImpl(name, parentStub, superClasses);
  }

  public void indexStub(final PyClassStub stub, final IndexSink sink) {
    final String name = stub.getName();
    if (name != null) {
      sink.occurence(PyClassNameIndex.KEY, name);
    }
    for(String s: stub.getSuperClasses()) {
      sink.occurence(PySuperClassIndex.KEY, s);
    }
  }
}