// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.trace.impl;

import com.intellij.debugger.streams.core.trace.NextAwareState;
import com.intellij.debugger.streams.core.trace.TraceElement;
import com.intellij.debugger.streams.core.wrapper.StreamCall;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Vitaliy.Bibaev
 */
final class FirstStateImpl extends StateBase implements NextAwareState {
  private final StreamCall myNextCall;
  private final Map<TraceElement, List<TraceElement>> myToNext;

  FirstStateImpl(@NotNull List<TraceElement> elements,
                 @NotNull StreamCall nextCall,
                 @NotNull Map<TraceElement, List<TraceElement>> toNextMapping) {
    super(elements);
    myNextCall = nextCall;
    myToNext = toNextMapping;
  }

  @Override
  public @NotNull StreamCall getNextCall() {
    return myNextCall;
  }

  @Override
  public @NotNull List<TraceElement> getNextValues(@NotNull TraceElement value) {
    return myToNext.getOrDefault(value, Collections.emptyList());
  }
}
