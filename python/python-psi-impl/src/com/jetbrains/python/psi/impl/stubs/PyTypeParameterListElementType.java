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
import com.jetbrains.python.psi.PyTypeParameterList;
import com.jetbrains.python.psi.impl.PyTypeParameterListImpl;
import com.jetbrains.python.psi.stubs.PyTypeParameterListStub;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class PyTypeParameterListElementType extends PyStubElementType<PyTypeParameterListStub, PyTypeParameterList> {

  public PyTypeParameterListElementType() {
    super("TYPE_PARAMETER_LIST");
  }

  @Override
  public PyTypeParameterList createPsi(@NotNull PyTypeParameterListStub stub) {
    return new PyTypeParameterListImpl(stub);
  }

  @Override
  @NotNull
  public PyTypeParameterListStub createStub(@NotNull PyTypeParameterList psi, StubElement<? extends PsiElement> parentStub) {
    return new PyTypeParameterListStubImpl(parentStub, getStubElementType());
  }

  @Override
  public void serialize(@NotNull PyTypeParameterListStub stub, @NotNull StubOutputStream dataStream) throws IOException {

  }

  @Override
  @NotNull
  public PyTypeParameterListStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new PyTypeParameterListStubImpl(parentStub, getStubElementType());
  }

  @Override
  @NotNull
  public PsiElement createElement(@NotNull ASTNode node) {
    return new PyTypeParameterListImpl(node);
  }

  protected IStubElementType<PyTypeParameterListStub, PyTypeParameterList> getStubElementType() {
    return PyStubElementTypes.TYPE_PARAMETER_LIST;
  }
}
