/*
 * @author max
 */
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.util.io.StringRef;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyNamedParameterImpl;
import com.jetbrains.python.psi.stubs.PyNamedParameterStub;

import java.io.IOException;

public class PyNamedParameterElementType extends PyStubElementType<PyNamedParameterStub, PyNamedParameter> {
  public PyNamedParameterElementType() {
    super("NAMED_PARAMETER");
  }

  public PyNamedParameter createPsi(final PyNamedParameterStub stub) {
    return new PyNamedParameterImpl(stub);
  }

  public PyNamedParameterStub createStub(final PyNamedParameter psi, final StubElement parentStub) {
    return new PyNamedParameterStubImpl(psi.getName(), psi.isPositionalContainer(), psi.isKeywordContainer(), parentStub);
  }

  public PsiElement createElement(final ASTNode node) {
    return new PyNamedParameterImpl(node);
  }

  public void serialize(final PyNamedParameterStub stub, final StubOutputStream dataStream)
      throws IOException {
    dataStream.writeName(stub.getName());
    dataStream.writeBoolean(stub.isKeywordContainer());
    dataStream.writeBoolean(stub.isPositionalContainer());
  }

  public PyNamedParameterStub deserialize(final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    String name = StringRef.toString(dataStream.readName());
    boolean keyword = dataStream.readBoolean();
    boolean positional = dataStream.readBoolean();
    return new PyNamedParameterStubImpl(name, positional, keyword, parentStub);
  }
}