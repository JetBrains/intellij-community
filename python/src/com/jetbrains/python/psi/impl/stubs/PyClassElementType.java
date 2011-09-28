package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.*;
import com.intellij.util.io.StringRef;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyClassImpl;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyClassNameIndexInsensitive;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.stubs.PySuperClassIndex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class PyClassElementType extends PyStubElementType<PyClassStub, PyClass> {
  public PyClassElementType() {
    this("CLASS_DECLARATION");
  }

  public PyClassElementType(String debugName) {
    super(debugName);
  }

  public PsiElement createElement(final ASTNode node) {
    return new PyClassImpl(node);
  }

  public PyClass createPsi(final PyClassStub stub) {
    return new PyClassImpl(stub);
  }

  public PyClassStub createStub(final PyClass psi, final StubElement parentStub) {
    final PyExpression[] exprs = psi.getSuperClassExpressions();
    List<PyQualifiedName> superClasses = new ArrayList<PyQualifiedName>();
    for (final PyExpression expression : exprs) {
      superClasses.add(PyQualifiedName.fromExpression(expression));
    }
    return new PyClassStubImpl(psi.getName(), parentStub,
                               superClasses.toArray(new PyQualifiedName[superClasses.size()]),
                               ((PyClassImpl) psi).getOwnSlots(),
                               getStubElementType());
  }

  public void serialize(final PyClassStub pyClassStub, final StubOutputStream dataStream) throws IOException {
    dataStream.writeName(pyClassStub.getName());
    final PyQualifiedName[] classes = pyClassStub.getSuperClasses();
    dataStream.writeByte(classes.length);
    for(PyQualifiedName s: classes) {
      PyQualifiedName.serialize(s, dataStream);
    }
    PyFileElementType.writeNullableList(dataStream, pyClassStub.getSlots());
  }

  public PyClassStub deserialize(final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    String name = StringRef.toString(dataStream.readName());
    int superClassCount = dataStream.readByte();
    PyQualifiedName[] superClasses = new PyQualifiedName[superClassCount];
    for(int i=0; i<superClassCount; i++) {
      superClasses[i] = PyQualifiedName.deserialize(dataStream);
    }
    List<String> slots = PyFileElementType.readNullableList(dataStream);
    return new PyClassStubImpl(name, parentStub, superClasses, slots, getStubElementType());
  }

  public void indexStub(final PyClassStub stub, final IndexSink sink) {
    final String name = stub.getName();
    if (name != null) {
      sink.occurrence(PyClassNameIndex.KEY, name);
      sink.occurrence(PyClassNameIndexInsensitive.KEY, name.toLowerCase());
    }
    for(PyQualifiedName s: stub.getSuperClasses()) {
      if (s != null) {
        String className = s.getLastComponent();
        if (className != null) {
          sink.occurrence(PySuperClassIndex.KEY, className);
        }
      }
    }
  }

  protected IStubElementType getStubElementType() {
    return PyElementTypes.CLASS_DECLARATION;
  }
}