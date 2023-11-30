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

  private final PyTypeParameter.Kind myKind;


  public PyTypeParameterStubImpl(@Nullable String name,
                                 @NotNull PyTypeParameter.Kind type,
                                 @Nullable String boundExpressionText,
                                 @Nullable StubElement parent,
                                 @NotNull IStubElementType stubElementType) {
    super(parent, stubElementType);
    myName = name;
    myKind = type;
    myBoundExpressionText = boundExpressionText;
  }

  @Override
  @Nullable
  public String getBoundExpressionText() {
    return myBoundExpressionText;
  }

  @Override
  @NotNull
  public PyTypeParameter.Kind getKind() {
    return myKind;
  }

  @Override
  @Nullable
  public String getName() {
    return myName;
  }

  @Override
  public String toString() {
    return "PyTypeParameterStub(name=" + myName + ", kind=" + myKind + ", bound=" + myBoundExpressionText + ")";
  }
}
