// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.ui.impl;

import com.intellij.debugger.streams.trace.IntermediateState;
import com.intellij.debugger.streams.trace.NextAwareState;
import com.intellij.debugger.streams.trace.PrevAwareState;
import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.debugger.streams.ui.PropagationDirection;
import com.intellij.debugger.streams.ui.TraceContainer;
import com.intellij.debugger.streams.ui.TraceController;
import com.intellij.debugger.streams.ui.ValuesSelectionListener;
import com.intellij.debugger.streams.wrapper.StreamCall;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * @author Vitaliy.Bibaev
 */
public class TraceControllerImpl implements TraceController, Disposable {
  private final List<TraceContainer> myTraceContainers = new CopyOnWriteArrayList<>();
  private final ValuesSelectionListener mySelectionListener;
  private final IntermediateState myState;
  private final PrevAwareState myToPrev;
  private final NextAwareState myToNext;

  private TraceController myPrevListener = null;
  private TraceController myNextListener = null;

  TraceControllerImpl(@NotNull IntermediateState state) {
    myState = state;
    myToPrev = state instanceof PrevAwareState ? (PrevAwareState)state : null;
    myToNext = state instanceof NextAwareState ? (NextAwareState)state : null;

    mySelectionListener = elements -> {
      selectAll(elements);

      propagateForward(elements);
      propagateBackward(elements);
    };
  }

  @Override
  public void dispose() {
  }

  void setPreviousController(@NotNull TraceController listener) {
    myPrevListener = listener;
  }

  void setNextController(@NotNull TraceController listener) {
    myNextListener = listener;
  }

  @NotNull
  @Override
  public List<Value> getValues() {
    return myState.getRawValues();
  }

  @NotNull
  public List<TraceElement> getTrace() {
    return myState.getTrace();
  }

  @Nullable
  @Override
  public StreamCall getNextCall() {
    return myToNext == null ? null : myToNext.getNextCall();
  }

  @Nullable
  @Override
  public StreamCall getPrevCall() {
    return myToPrev == null ? null : myToPrev.getPrevCall();
  }

  @NotNull
  @Override
  public List<TraceElement> getNextValues(@NotNull TraceElement element) {
    return myToNext == null ? Collections.emptyList() : myToNext.getNextValues(element);
  }

  @NotNull
  @Override
  public List<TraceElement> getPrevValues(@NotNull TraceElement element) {
    return myToPrev == null ? Collections.emptyList() : myToPrev.getPrevValues(element);
  }

  @Override
  public boolean isSelectionExists(@NotNull PropagationDirection direction) {
    for (final TraceContainer container : myTraceContainers) {
      if (container.highlightedExists()) {
        return true;
      }
    }

    return PropagationDirection.FORWARD.equals(direction)
           ? selectionExistsForward()
           : selectionExistsBackward();
  }

  @Override
  public void register(@NotNull TraceContainer listener) {
    myTraceContainers.add(listener);
    listener.addSelectionListener(mySelectionListener);
    Disposer.register(this, listener);
  }

  @Override
  public void highlightingChanged(@NotNull List<TraceElement> values, @NotNull PropagationDirection direction) {
    highlightAll(values);
    propagate(values, direction);
  }

  private void propagate(@NotNull List<TraceElement> values, @NotNull PropagationDirection direction) {
    if (direction.equals(PropagationDirection.BACKWARD)) {
      propagateBackward(values);
    }
    else {
      propagateForward(values);
    }
  }

  private void propagateForward(@NotNull List<TraceElement> values) {
    if (myNextListener == null) return;
    final List<TraceElement> nextValues =
      values.stream().flatMap(x -> getNextValues(x).stream()).collect(Collectors.toList());
    myNextListener.highlightingChanged(nextValues, PropagationDirection.FORWARD);
  }

  private void propagateBackward(@NotNull List<TraceElement> values) {
    if (myPrevListener == null) return;
    final List<TraceElement> prevValues =
      values.stream().flatMap(x -> getPrevValues(x).stream()).collect(Collectors.toList());
    myPrevListener.highlightingChanged(prevValues, PropagationDirection.BACKWARD);
  }

  private void highlightAll(@NotNull List<TraceElement> values) {
    for (final TraceContainer listener : myTraceContainers) {
      listener.highlight(values);
    }
  }

  private void selectAll(@NotNull List<TraceElement> values) {
    for (final TraceContainer listener : myTraceContainers) {
      listener.select(values);
    }
  }

  private boolean selectionExistsForward() {
    return myNextListener != null && myNextListener.isSelectionExists(PropagationDirection.FORWARD);
  }

  private boolean selectionExistsBackward() {
    return myPrevListener != null && myPrevListener.isSelectionExists(PropagationDirection.BACKWARD);
  }
}
