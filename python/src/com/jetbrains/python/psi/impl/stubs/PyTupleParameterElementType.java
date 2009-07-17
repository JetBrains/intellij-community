package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.PyTupleParameter;
import com.jetbrains.python.psi.impl.PyTupleParameterImpl;
import com.jetbrains.python.psi.stubs.PyTupleParameterStub;

import java.io.IOException;

/**
 * Does actual storing and loading of tuple parameter stub. Not much to do.
 */
public class PyTupleParameterElementType extends PyStubElementType<PyTupleParameterStub, PyTupleParameter> {

  public PyTupleParameterElementType() {
    super("TUPLE_PARAMETER");
  }

  public PsiElement createElement(ASTNode node) {
    return new PyTupleParameterImpl(node);
  }

  public PyTupleParameter createPsi(PyTupleParameterStub stub) {
    return new PyTupleParameterImpl(stub);
  }

  public PyTupleParameterStub createStub(PyTupleParameter psi, StubElement parentStub) {
    return new PyTupleParameterStubImpl(parentStub);
  }

  public PyTupleParameterStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new PyTupleParameterStubImpl(parentStub);
  }

  public void serialize(PyTupleParameterStub stub, StubOutputStream dataStream) throws IOException {
    // nothing; children serialize themselves
  }
}
