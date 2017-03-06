package com.intellij.debugger.streams.trace.smart.handler;

import com.intellij.debugger.streams.wrapper.StreamCall;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class ProducerHandler extends HandlerBase {
  @NotNull
  @Override
  protected List<Variable> getVariables() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<StreamCall> additionalCallsBefore() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<StreamCall> additionalCallsAfter() {
    return Collections.singletonList(new PeekCall("x -> time.incrementAndGet()"));
  }

  @NotNull
  @Override
  public String prepareResult() {
    return "";
  }

  @NotNull
  @Override
  public String getResultExpression() {
    return "null";
  }
}
