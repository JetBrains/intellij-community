/*
 * @author max
 */
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.util.io.StringRef;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyNamedParameterImpl;
import com.jetbrains.python.psi.stubs.PyNamedParameterStub;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class PyNamedParameterElementType extends PyStubElementType<PyNamedParameterStub, PyNamedParameter> {
  private static final int POSITIONAL_CONTAINER = 1;
  private static final int KEYWORD_CONTAINER = 2;
  private static final int HAS_DEFAULT_VALUE = 4;

  public PyNamedParameterElementType() {
    this("NAMED_PARAMETER");
  }

  public PyNamedParameterElementType(String debugName) {
    super(debugName);
  }

  public PyNamedParameter createPsi(@NotNull final PyNamedParameterStub stub) {
    return new PyNamedParameterImpl(stub);
  }

  public PyNamedParameterStub createStub(@NotNull final PyNamedParameter psi, final StubElement parentStub) {
    return new PyNamedParameterStubImpl(psi.getName(), psi.isPositionalContainer(), psi.isKeywordContainer(), psi.hasDefaultValue(),
                                        parentStub, getStubElementType());
  }

  public PsiElement createElement(@NotNull final ASTNode node) {
    return new PyNamedParameterImpl(node);
  }

  public void serialize(final PyNamedParameterStub stub, final StubOutputStream dataStream)
      throws IOException {
    dataStream.writeName(stub.getName());

    byte flags = 0;
    if (stub.isPositionalContainer()) flags |= POSITIONAL_CONTAINER;
    if (stub.isKeywordContainer()) flags |= KEYWORD_CONTAINER;
    if (stub.hasDefaultValue()) flags |= HAS_DEFAULT_VALUE;
    dataStream.writeByte(flags);
  }

  public PyNamedParameterStub deserialize(final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    String name = StringRef.toString(dataStream.readName());
    byte flags = dataStream.readByte();
    return new PyNamedParameterStubImpl(name,
                                        (flags & POSITIONAL_CONTAINER) != 0,
                                        (flags & KEYWORD_CONTAINER) != 0,
                                        (flags & HAS_DEFAULT_VALUE) != 0,
                                        parentStub,
                                        getStubElementType());
  }

  @Override
  public boolean shouldCreateStub(ASTNode node) {
    final ASTNode paramList = node.getTreeParent();
    if (paramList != null) {
      final ASTNode container = paramList.getTreeParent();
      if (container != null && container.getElementType() == PyElementTypes.LAMBDA_EXPRESSION) {
        return false;
      }
    }
    return super.shouldCreateStub(node);
  }

  protected IStubElementType getStubElementType() {
    return PyElementTypes.NAMED_PARAMETER;
  }
}