package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.*;
import com.intellij.util.io.StringRef;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyClassImpl;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyClassNameIndexInsensitive;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.stubs.PySuperClassIndex;
import org.jetbrains.annotations.NotNull;

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

  public PsiElement createElement(@NotNull final ASTNode node) {
    return new PyClassImpl(node);
  }

  public PyClass createPsi(@NotNull final PyClassStub stub) {
    return new PyClassImpl(stub);
  }

  public PyClassStub createStub(@NotNull final PyClass psi, final StubElement parentStub) {
    final PyExpression[] exprs = psi.getSuperClassExpressions();
    List<PyQualifiedName> superClasses = new ArrayList<PyQualifiedName>();
    for (PyExpression expression : exprs) {
      expression = PyClassImpl.unfoldClass(expression);
      superClasses.add(PyQualifiedName.fromExpression(expression));
    }
    final PyStringLiteralExpression docStringExpression = psi.getDocStringExpression();
    return new PyClassStubImpl(psi.getName(), parentStub,
                               superClasses.toArray(new PyQualifiedName[superClasses.size()]),
                               psi.getOwnSlots(),
                               PyPsiUtils.strValue(docStringExpression),
                               getStubElementType());
  }

  public void serialize(@NotNull final PyClassStub pyClassStub, @NotNull final StubOutputStream dataStream) throws IOException {
    dataStream.writeName(pyClassStub.getName());
    final PyQualifiedName[] classes = pyClassStub.getSuperClasses();
    dataStream.writeByte(classes.length);
    for (PyQualifiedName s : classes) {
      PyQualifiedName.serialize(s, dataStream);
    }
    PyFileElementType.writeNullableList(dataStream, pyClassStub.getSlots());
    final String docString = pyClassStub.getDocString();
    dataStream.writeUTFFast(docString != null ? docString : "");
  }

  @NotNull
  public PyClassStub deserialize(@NotNull final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    String name = StringRef.toString(dataStream.readName());
    int superClassCount = dataStream.readByte();
    PyQualifiedName[] superClasses = new PyQualifiedName[superClassCount];
    for (int i = 0; i < superClassCount; i++) {
      superClasses[i] = PyQualifiedName.deserialize(dataStream);
    }
    List<String> slots = PyFileElementType.readNullableList(dataStream);
    final String docString = dataStream.readUTFFast();
    return new PyClassStubImpl(name, parentStub, superClasses, slots, docString.length() > 0 ? docString : null, getStubElementType());
  }

  public void indexStub(@NotNull final PyClassStub stub, @NotNull final IndexSink sink) {
    final String name = stub.getName();
    if (name != null) {
      sink.occurrence(PyClassNameIndex.KEY, name);
      sink.occurrence(PyClassNameIndexInsensitive.KEY, name.toLowerCase());
    }
    for (PyQualifiedName s : stub.getSuperClasses()) {
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