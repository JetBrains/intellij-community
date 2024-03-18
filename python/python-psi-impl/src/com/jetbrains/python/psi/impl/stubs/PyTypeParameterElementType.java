package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyStubElementTypes;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.PyTypeParameter;
import com.jetbrains.python.psi.impl.PyTypeParameterImpl;
import com.jetbrains.python.psi.stubs.PyTypeParameterStub;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class PyTypeParameterElementType extends PyStubElementType<PyTypeParameterStub, PyTypeParameter> {

  public PyTypeParameterElementType() {
    super("TYPE_PARAMETER");
  }

  @Override
  public PyTypeParameter createPsi(@NotNull PyTypeParameterStub stub) {
    return new PyTypeParameterImpl(stub);
  }

  @Override
  @NotNull
  public PyTypeParameterStub createStub(@NotNull PyTypeParameter psi, StubElement<? extends PsiElement> parentStub) {
    return new PyTypeParameterStubImpl(psi.getName(), psi.getKind(), psi.getBoundExpression() != null ? psi.getBoundExpression().getText() : null,
                                       parentStub, getStubElementType());
  }

  @Override
  public void serialize(@NotNull PyTypeParameterStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    dataStream.writeVarInt(stub.getKind().getIndex());
    dataStream.writeName(stub.getBoundExpressionText());
  }

  @Override
  @NotNull
  public PyTypeParameterStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    String name = dataStream.readNameString();
    PyTypeParameter.Kind kind = PyTypeParameter.Kind.fromIndex(dataStream.readVarInt());
    String boundExpressionText = dataStream.readNameString();
    return new PyTypeParameterStubImpl(name,
                                       kind,
                                       boundExpressionText,
                                       parentStub, getStubElementType());
  }

  @Override
  @NotNull
  public PsiElement createElement(@NotNull ASTNode node) {
    return new PyTypeParameterImpl(node);
  }

  @NotNull
  protected IStubElementType getStubElementType() {
    return PyStubElementTypes.TYPE_PARAMETER;
  }
}
