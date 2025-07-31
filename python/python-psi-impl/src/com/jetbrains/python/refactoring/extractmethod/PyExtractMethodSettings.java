package com.jetbrains.python.refactoring.extractmethod;

import com.intellij.refactoring.extractMethod.ExtractMethodSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyExtractMethodSettings implements ExtractMethodSettings<Object> {
  private final String myMethodName;
  private final PyVariableData @NotNull [] myVariableData;
  private final String myReturnTypeName;
  private final boolean myUseTypeAnnotations;

  public PyExtractMethodSettings(@NotNull String methodName,
                                 PyVariableData @NotNull [] variableData,
                                 String returnTypeName,
                                 boolean useTypeAnnotations) {
    myMethodName = methodName;
    myVariableData = variableData;
    myReturnTypeName = returnTypeName;
    myUseTypeAnnotations = useTypeAnnotations;
  }

  @Override
  public @NotNull String getMethodName() {
    return myMethodName;
  }

  @Override
  public PyVariableData @NotNull [] getAbstractVariableData() {
    return myVariableData;
  }

  public String getReturnTypeName() {
    return myReturnTypeName;
  }

  public boolean isUseTypeAnnotations() {
    return myUseTypeAnnotations;
  }

  @Override
  public @Nullable Object getVisibility() {
    return null;
  }
}
