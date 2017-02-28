package com.intellij.debugger.streams.trace.smart.handler;

import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class VariableImpl implements Variable {
  private final String myName;
  private final String myType;
  private final String myInitialExpression;

  public VariableImpl(String name, String type, String initialExpression) {
    myName = name;
    myType = type;
    myInitialExpression = initialExpression;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public String getTypeName() {
    return myType;
  }

  @NotNull
  @Override
  public String getInitialExpression() {
    return myInitialExpression;
  }
}
