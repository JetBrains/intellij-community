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
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author yole
 */
public class PyExceptPartElementType extends PyStubElementType<PyExceptPartStub, PyExceptPart> {
  public PyExceptPartElementType() {
    super("EXCEPT_PART");
  }

  @Override
  public PsiElement createElement(@NotNull ASTNode node) {
    return new PyExceptPartImpl(node);
  }

  @Override
  public PyExceptPart createPsi(@NotNull PyExceptPartStub stub) {
    return new PyExceptPartImpl(stub);
  }

  @Override
  public PyExceptPartStub createStub(@NotNull PyExceptPart psi, StubElement parentStub) {
    return new PyExceptPartStubImpl(parentStub);
  }

  @Override
  public void serialize(@NotNull PyExceptPartStub stub, @NotNull StubOutputStream dataStream) throws IOException {
  }

  @NotNull
  @Override
  public PyExceptPartStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new PyExceptPartStubImpl(parentStub);
  }
}
