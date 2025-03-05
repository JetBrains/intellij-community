package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyTypeParameter;
import com.jetbrains.python.psi.stubs.PyTypeParameterStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyTypeParameterStubImpl extends StubBase<PyTypeParameter> implements PyTypeParameterStub {

  private final String myName;
  private final String myBoundExpressionText;
  private final String myDefaultExpressionText;

  private final PyTypeParameter.Kind myKind;


  public PyTypeParameterStubImpl(@Nullable String name,
                                 @NotNull PyTypeParameter.Kind type,
                                 @Nullable String boundExpressionText,
                                 @Nullable String defaultExpressionText,
                                 @Nullable StubElement parent,
                                 @NotNull IStubElementType stubElementType) {
    super(parent, stubElementType);
    myName = name;
    myKind = type;
    myBoundExpressionText = boundExpressionText;
    myDefaultExpressionText = defaultExpressionText;
  }

  @Override
  public @Nullable String getBoundExpressionText() {
    return myBoundExpressionText;
  }

  @Override
  public @Nullable String getDefaultExpressionText() {
    return myDefaultExpressionText;
  }

  @Override
  public @NotNull PyTypeParameter.Kind getKind() {
    return myKind;
  }

  @Override
  public @Nullable String getName() {
    return myName;
  }

  @Override
  public String toString() {
    return "PyTypeParameterStub(name=" + myName +
           ", kind=" + myKind +
           ", bound=" + myBoundExpressionText +
           ", default=" + myDefaultExpressionText + ")";
  }
}
