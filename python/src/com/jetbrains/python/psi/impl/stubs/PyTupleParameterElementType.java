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
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Does actual storing and loading of tuple parameter stub. Not much to do.
 */
public class PyTupleParameterElementType extends PyStubElementType<PyTupleParameterStub, PyTupleParameter> {

  public PyTupleParameterElementType() {
    super("TUPLE_PARAMETER");
  }

  public PsiElement createElement(@NotNull final ASTNode node) {
    return new PyTupleParameterImpl(node);
  }

  public PyTupleParameter createPsi(@NotNull PyTupleParameterStub stub) {
    return new PyTupleParameterImpl(stub);
  }

  public PyTupleParameterStub createStub(@NotNull PyTupleParameter psi, StubElement parentStub) {
    return new PyTupleParameterStubImpl(psi.hasDefaultValue(), parentStub);
  }

  @NotNull
  public PyTupleParameterStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    boolean hasDefaultValue = dataStream.readBoolean();
    return new PyTupleParameterStubImpl(hasDefaultValue, parentStub);
  }

  public void serialize(@NotNull PyTupleParameterStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeBoolean(stub.hasDefaultValue());
  }
}
