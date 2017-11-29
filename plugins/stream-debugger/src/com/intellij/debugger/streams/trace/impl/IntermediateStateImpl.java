// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.impl;

import com.intellij.debugger.streams.trace.BidirectionalAwareState;
import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.debugger.streams.wrapper.StreamCall;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Vitaliy.Bibaev
 */
public class IntermediateStateImpl extends StateBase implements BidirectionalAwareState {
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

  @NotNull
  @Override
  public StreamCall getPrevCall() {
    return myPrevCall;
  }

  @NotNull
  @Override
  public List<TraceElement> getPrevValues(@NotNull TraceElement value) {
    return myToPrev.getOrDefault(value, Collections.emptyList());
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
