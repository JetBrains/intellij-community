package com.intellij.debugger.streams.trace.smart;

import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class TraceElementImpl implements TraceElement, Comparable<TraceElement> {
  private final int myTime;
  private final Value myValue;

  public TraceElementImpl(int time, @NotNull Value value) {
    myTime = time;
    myValue = value;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof TraceElement) {
      final int time = ((TraceElement)obj).getTime();
      return time == myTime;
    }

    return false;
  }

  @Override
  public int hashCode() {
    return getTime();
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

  @Override
  public int compareTo(@NotNull TraceElement other) {
    return Integer.compare(myTime, other.getTime());
  }
}
