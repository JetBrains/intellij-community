// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.trace.impl;

import com.intellij.debugger.streams.core.trace.BidirectionalAwareState;
import com.intellij.debugger.streams.core.trace.TraceElement;
import com.intellij.debugger.streams.core.wrapper.StreamCall;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Vitaliy.Bibaev
 */
final class IntermediateStateImpl extends StateBase implements BidirectionalAwareState {
  private final Map<TraceElement, List<TraceElement>> myToPrev;
  private final Map<TraceElement, List<TraceElement>> myToNext;
  private final StreamCall myNextCall;
  private final StreamCall myPrevCall;

  IntermediateStateImpl(@NotNull List<TraceElement> elements,
                        @NotNull StreamCall prevCall, @NotNull StreamCall nextCall,
                        @NotNull Map<TraceElement, List<TraceElement>> toPrevMapping,
                        @NotNull Map<TraceElement, List<TraceElement>> toNextMapping) {
    super(elements);
    myToPrev = toPrevMapping;
    myToNext = toNextMapping;

    myPrevCall = prevCall;
    myNextCall = nextCall;
  }

  @Override
  public @NotNull StreamCall getPrevCall() {
    return myPrevCall;
  }

  @Override
  public @NotNull List<TraceElement> getPrevValues(@NotNull TraceElement value) {
    return myToPrev.getOrDefault(value, Collections.emptyList());
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
