/*
 * @author max
 */
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyParameterListImpl;
import com.jetbrains.python.psi.stubs.PyParameterListStub;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class PyParameterListElementType extends PyStubElementType<PyParameterListStub, PyParameterList> {
  public PyParameterListElementType() {
    this("PARAMETER_LIST");
  }

  public PyParameterListElementType(String debugName) {
    super(debugName);
  }

  public PyParameterList createPsi(@NotNull final PyParameterListStub stub) {
    return new PyParameterListImpl(stub);
  }

  public PyParameterListStub createStub(@NotNull final PyParameterList psi, final StubElement parentStub) {
    return new PyParameterListStubImpl(parentStub, getStubElementType());
  }

  public PsiElement createElement(@NotNull final ASTNode node) {
    return new PyParameterListImpl(node);
  }

  public void serialize(final PyParameterListStub stub, final StubOutputStream dataStream)
      throws IOException {
  }

  public PyParameterListStub deserialize(final StubInputStream dataStream, final StubElement parentStub)
      throws IOException {
    return new PyParameterListStubImpl(parentStub, getStubElementType());
  }

  protected IStubElementType getStubElementType() {
    return PyElementTypes.PARAMETER_LIST;
  }

  @Override
  public boolean shouldCreateStub(ASTNode node) {
    if (node.getTreeParent().getElementType() == PyElementTypes.LAMBDA_EXPRESSION) {
      return false;
    }
    return super.shouldCreateStub(node);
  }
}