package com.jetbrains.python.psi.impl.stubs;

import com.google.common.collect.RangeSet;
import com.intellij.openapi.util.Version;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyTypeAliasStatement;
import com.jetbrains.python.psi.stubs.PyTypeAliasStatementStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyTypeAliasStatementStubImpl extends PyVersionSpecificStubBase<PyTypeAliasStatement> implements PyTypeAliasStatementStub {

  private final String myName;
  private final String myTypeExpressionText;

  public PyTypeAliasStatementStubImpl(@Nullable String name,
                                      @Nullable String typeExpressionText,
                                      @Nullable StubElement parent,
                                      @NotNull IStubElementType stubElementType,
                                      @NotNull RangeSet<Version> versions) {
    super(parent, stubElementType, versions);
    myName = name;
    myTypeExpressionText = typeExpressionText;
  }

  @Override
  public @Nullable String getName() {
    return myName;
  }

  @Override
  public @Nullable String getTypeExpressionText() {
    return myTypeExpressionText;
  }

  @Override
  public String toString() {
    return "PyTypeAliasStatementStubImpl{" +
           "myName='" + myName + '\'' +
           ", myTypeExpressionText='" + myTypeExpressionText + '\'' +
           '}';
  }
}
