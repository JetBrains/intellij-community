package com.intellij.debugger.streams.trace.smart.handler.type;

import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class GenericTypeImpl implements GenericType {
  private final String myPrimitiveName;
  private final String myGenericName;

  public GenericTypeImpl(@NotNull String primitiveName, @NotNull String genericName) {
    myPrimitiveName = primitiveName;
    myGenericName = genericName;
  }

  @NotNull
  @Override
  public String getVariableName() {
    return myPrimitiveName;
  }

  @NotNull
  @Override
  public String getGenericTypeName() {
    return myGenericName;
  }
}
