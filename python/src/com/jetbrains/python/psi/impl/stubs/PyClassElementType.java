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
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyClassImpl;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.stubs.PySuperClassIndex;

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

  public void serialize(final PyClassStub pyClassStub, final StubOutputStream dataStream) throws IOException {
    dataStream.writeName(pyClassStub.getName());
    final String[] classes = pyClassStub.getSuperClasses();
    dataStream.writeByte(classes.length);
    for(String s: classes) {
      dataStream.writeName(s);
    }
  }

  public PyClassStub deserialize(final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    String name = StringRef.toString(dataStream.readName());
    int superClassCount = dataStream.readByte();
    String[] superClasses = new String[superClassCount];
    for(int i=0; i<superClassCount; i++) {
      superClasses[i] = StringRef.toString(dataStream.readName());
    }
    return new PyClassStubImpl(name, parentStub, superClasses);
  }

  public void indexStub(final PyClassStub stub, final IndexSink sink) {
    final String name = stub.getName();
    if (name != null) {
      sink.occurrence(PyClassNameIndex.KEY, name);
    }
    for(String s: stub.getSuperClasses()) {
      sink.occurrence(PySuperClassIndex.KEY, s);
    }
  }
}