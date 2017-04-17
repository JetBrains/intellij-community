/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.streams.trace.impl;

import com.intellij.debugger.streams.trace.TraceElement;
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
