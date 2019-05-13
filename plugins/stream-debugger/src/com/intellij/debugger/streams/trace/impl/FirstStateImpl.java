// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.impl;

import com.intellij.debugger.streams.trace.NextAwareState;
import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.debugger.streams.wrapper.StreamCall;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Vitaliy.Bibaev
 */
public class FirstStateImpl extends StateBase implements NextAwareState {
  private final StreamCall myNextCall;
  private final Map<TraceElement, List<TraceElement>> myToNext;

  FirstStateImpl(@NotNull List<TraceElement> elements,
                 @NotNull StreamCall nextCall,
                 @NotNull Map<TraceElement, List<TraceElement>> toNextMapping) {
    super(elements);
    myNextCall = nextCall;
    myToNext = toNextMapping;
  }

  @NotNull
  @Override
  public StreamCall getNextCall() {
    return myNextCall;
  }

  @NotNull
  @Override
  public List<TraceElement> getNextValues(@NotNull TraceElement value) {
    return myToNext.getOrDefault(value, Collections.emptyList());
  }
}
