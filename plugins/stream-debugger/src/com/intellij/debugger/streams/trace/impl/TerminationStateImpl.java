// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.impl;

import com.intellij.debugger.streams.trace.PrevAwareState;
import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.debugger.streams.wrapper.StreamCall;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Vitaliy.Bibaev
 */
public class TerminationStateImpl extends StateBase implements PrevAwareState {
  private final TraceElement myResult;
  private final StreamCall myPrevCall;
  private final Map<TraceElement, List<TraceElement>> myToPrev;

  TerminationStateImpl(@NotNull TraceElement result,
                       @NotNull StreamCall prevCall,
                       @NotNull List<TraceElement> elements,
                       @NotNull Map<TraceElement, List<TraceElement>> toPrevMapping) {
    super(elements);
    myResult = result;
    myPrevCall = prevCall;
    myToPrev = toPrevMapping;
  }

  @NotNull
  @Override
  public List<Value> getRawValues() {
    return Collections.singletonList(myResult.getValue());
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
}
