package com.intellij.debugger.streams.trace.smart;

import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class TraceElementImpl implements TraceElement {
  private final int myTime;
  private final Value myValue;

  public TraceElementImpl(int time, @NotNull Value value) {
    myTime = time;
    myValue = value;
  }

  @Override
  public int getTime() {
    return myTime;
  }

  @NotNull
  @Override
  public Value getValue() {
    return myValue;
  }
}
