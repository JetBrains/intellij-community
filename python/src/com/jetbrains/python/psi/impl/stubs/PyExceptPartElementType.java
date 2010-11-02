package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyExceptPartImpl;
import com.jetbrains.python.psi.stubs.PyExceptPartStub;

import java.io.IOException;

/**
 * @author yole
 */
public class PyExceptPartElementType extends PyStubElementType<PyExceptPartStub, PyExceptPart> {
  public PyExceptPartElementType() {
    super("EXCEPT_PART");
  }

  @Override
  public PsiElement createElement(ASTNode node) {
    return new PyExceptPartImpl(node);
  }

  @Override
  public PyExceptPart createPsi(PyExceptPartStub stub) {
    return new PyExceptPartImpl(stub);
  }

  @Override
  public PyExceptPartStub createStub(PyExceptPart psi, StubElement parentStub) {
    return new PyExceptPartStubImpl(parentStub);
  }

  @Override
  public void serialize(PyExceptPartStub stub, StubOutputStream dataStream) throws IOException {
  }

  @Override
  public PyExceptPartStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new PyExceptPartStubImpl(parentStub);
  }
}
