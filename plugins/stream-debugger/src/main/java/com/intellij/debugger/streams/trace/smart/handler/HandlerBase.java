package com.intellij.debugger.streams.trace.smart.handler;

import com.intellij.debugger.streams.trace.impl.TraceExpressionBuilderImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public abstract class HandlerBase implements TraceExpressionBuilderImpl.StreamCallTraceHandler {
  private static final String DECLARATION_FORMAT = "final %s %s = %s;" + TraceExpressionBuilderImpl.LINE_SEPARATOR;

  @NotNull
  @Override
  final public String additionalVariablesDeclaration() {
    final StringBuilder stringBuilder = new StringBuilder();
    final List<Variable> variables = getVariables();
    for (final Variable variable : variables) {
      stringBuilder.append(String.format(DECLARATION_FORMAT, variable.getTypeName(), variable.getName(), variable.getInitialExpression()));
    }

    return stringBuilder.toString();
  }

  @NotNull
  protected abstract List<Variable> getVariables();
}
