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
import com.jetbrains.python.psi.PyTypeAliasStatement;
import com.jetbrains.python.psi.impl.PyTypeAliasStatementImpl;
import com.jetbrains.python.psi.stubs.PyTypeAliasStatementStub;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class PyTypeAliasStatementElementType extends PyStubElementType<PyTypeAliasStatementStub, PyTypeAliasStatement> {

  public PyTypeAliasStatementElementType() {
    super("TYPE_ALIAS_STATEMENT");
  }

  @Override
  @NotNull
  public PsiElement createElement(@NotNull ASTNode node) {
    return new PyTypeAliasStatementImpl(node);
  }

  @Override
  public PyTypeAliasStatement createPsi(@NotNull PyTypeAliasStatementStub stub) {
    return new PyTypeAliasStatementImpl(stub);
  }

  @Override
  @NotNull
  public PyTypeAliasStatementStub createStub(@NotNull PyTypeAliasStatement psi, StubElement<? extends PsiElement> parentStub) {
    return new PyTypeAliasStatementStubImpl(psi.getName(), (psi.getTypeExpression() != null ? psi.getTypeExpression().getText() : null),
                                            parentStub, getStubElementType());
  }

  @Override
  public void serialize(@NotNull PyTypeAliasStatementStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    dataStream.writeName(stub.getTypeExpressionText());
  }

  @Override
  @NotNull
  public PyTypeAliasStatementStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    String name = dataStream.readNameString();
    String typeExpressionText = dataStream.readNameString();

    return new PyTypeAliasStatementStubImpl(name, typeExpressionText, parentStub, getStubElementType());
  }

  @NotNull
  protected IStubElementType getStubElementType() {
    return PyStubElementTypes.TYPE_ALIAS_STATEMENT;
  }
}
