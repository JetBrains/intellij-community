package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyTypeAliasStatement;
import com.jetbrains.python.psi.stubs.PyTypeAliasStatementStub;
import com.jetbrains.python.psi.stubs.PyVersionRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyTypeAliasStatementStubImpl extends PyVersionSpecificStubBase<PyTypeAliasStatement> implements PyTypeAliasStatementStub {

  private final String myName;
  private final String myTypeExpressionText;

  public PyTypeAliasStatementStubImpl(@Nullable String name,
                                      @Nullable String typeExpressionText,
                                      @Nullable StubElement parent,
                                      @NotNull IStubElementType stubElementType,
                                      @NotNull PyVersionRange versionRange) {
    super(parent, stubElementType, versionRange);
    myName = name;
    myTypeExpressionText = typeExpressionText;
  }

  @Override
  @Nullable
  public String getName() {
    return myName;
  }

  @Override
  @Nullable
  public String getTypeExpressionText() {
    return myTypeExpressionText;
  }

  @Override
  public String toString() {
    return "PyTypeAliasStatementStub(name=" + myName + ", type expression=" + myTypeExpressionText +")";
  }
}
