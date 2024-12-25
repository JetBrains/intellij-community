package com.jetbrains.python.psi.impl.stubs;

import com.google.common.collect.RangeSet;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Version;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.jetbrains.python.PyStubElementTypes;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.PyTypeAliasStatement;
import com.jetbrains.python.psi.impl.PyTypeAliasStatementImpl;
import com.jetbrains.python.psi.stubs.PyTypeAliasStatementStub;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class PyTypeAliasStatementElementType extends PyStubElementType<PyTypeAliasStatementStub, PyTypeAliasStatement> {

  public PyTypeAliasStatementElementType() {
    super("TYPE_ALIAS_STATEMENT");
  }

  @Override
  public @NotNull PsiElement createElement(@NotNull ASTNode node) {
    return new PyTypeAliasStatementImpl(node);
  }

  @Override
  public PyTypeAliasStatement createPsi(@NotNull PyTypeAliasStatementStub stub) {
    return new PyTypeAliasStatementImpl(stub);
  }

  @Override
  public @NotNull PyTypeAliasStatementStub createStub(@NotNull PyTypeAliasStatement psi, StubElement<? extends PsiElement> parentStub) {
    return new PyTypeAliasStatementStubImpl(psi.getName(), (psi.getTypeExpression() != null ? psi.getTypeExpression().getText() : null),
                                            parentStub, getStubElementType(), PyVersionSpecificStubBaseKt.evaluateVersionsForElement(psi));
  }

  @Override
  public void serialize(@NotNull PyTypeAliasStatementStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    dataStream.writeName(stub.getTypeExpressionText());
    PyVersionSpecificStubBaseKt.serializeVersions(stub.getVersions(), dataStream);
  }

  @Override
  public @NotNull PyTypeAliasStatementStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    String name = dataStream.readNameString();
    String typeExpressionText = dataStream.readNameString();
    RangeSet<Version> versions = PyVersionSpecificStubBaseKt.deserializeVersions(dataStream);

    return new PyTypeAliasStatementStubImpl(name, typeExpressionText, parentStub, getStubElementType(), versions);
  }

  protected @NotNull IStubElementType getStubElementType() {
    return PyStubElementTypes.TYPE_ALIAS_STATEMENT;
  }
}
