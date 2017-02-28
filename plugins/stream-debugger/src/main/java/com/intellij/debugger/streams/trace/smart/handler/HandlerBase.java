package com.intellij.debugger.streams.trace.smart.handler;

import com.intellij.debugger.streams.trace.smart.MapToArrayTracerImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public abstract class HandlerBase implements MapToArrayTracerImpl.StreamCallTraceHandler {
  private static final String DECLARATION_FORMAT = "final %s %s = %s;" + System.lineSeparator();
  private final List<Variable> myVariables;

  public HandlerBase(@NotNull List<Variable> variables) {
    myVariables = variables;
  }

  @NotNull
  @Override
  public String additionalVariablesDeclaration() {
    final StringBuilder stringBuilder = new StringBuilder();
    for (final Variable variable : myVariables) {
      stringBuilder.append(String.format(DECLARATION_FORMAT, variable.getTypeName(), variable.getName(), variable.getInitialExpression()));
    }

    return stringBuilder.toString();
  }
}
