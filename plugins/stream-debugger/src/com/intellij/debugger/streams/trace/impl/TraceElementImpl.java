// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.impl;

import com.intellij.debugger.streams.trace.TraceElement;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vitaliy.Bibaev
 */
public class TraceElementImpl implements TraceElement, Comparable<TraceElement> {
  private final int myTime;
  private final Value myValue;

  public TraceElementImpl(int time, @Nullable Value value) {
    myTime = time;
    myValue = value;
  }

  public static TraceElement ofResultValue(@Nullable Value streamResult) {
    return new TraceElementImpl(Integer.MAX_VALUE, streamResult);
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

  @Nullable
  @Override
  public Value getValue() {
    return myValue;
  }

  @Override
  public int compareTo(@NotNull TraceElement other) {
    return Integer.compare(myTime, other.getTime());
  }
}
