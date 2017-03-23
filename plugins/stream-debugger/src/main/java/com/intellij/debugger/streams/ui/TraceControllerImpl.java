package com.intellij.debugger.streams.ui;

import com.intellij.debugger.streams.resolve.ResolvedTrace;
import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.debugger.streams.wrapper.StreamCall;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * @author Vitaliy.Bibaev
 */
public class TraceControllerImpl implements TraceController, ValuesHighlightingListener {
  private static final ValuesHighlightingListener EMPTY_LISTENER = (values, direction) -> {
  };
  private final List<TraceContainer> myListeners = new CopyOnWriteArrayList<>();
  private final ValuesSelectionListener mySelectionListener;
  private final ResolvedTrace myResolvedTrace;

  private ValuesHighlightingListener myPrevListener = EMPTY_LISTENER;
  private ValuesHighlightingListener myNextListener = EMPTY_LISTENER;

  TraceControllerImpl(@NotNull ResolvedTrace trace) {
    myResolvedTrace = trace;
    mySelectionListener = elements -> {
      selectAll(elements);

      propagateForward(elements);
      propagateBackward(elements);
    };
  }

  void setPreviousListener(@NotNull ValuesHighlightingListener listener) {
    myPrevListener = listener;
  }

  void setNextListener(@NotNull ValuesHighlightingListener listener) {
    myNextListener = listener;
  }

  @NotNull
  @Override
  public List<TraceElement> getValues() {
    return myResolvedTrace.getValues();
  }

  @NotNull
  @Override
  public StreamCall getCall() {
    return myResolvedTrace.getCall();
  }

  @Override
  public void register(@NotNull TraceContainer listener) {
    myListeners.add(listener);
    listener.addSelectionListener(mySelectionListener);
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
    final List<TraceElement> nextValues =
      values.stream().flatMap(x -> myResolvedTrace.getNextValues(x).stream()).collect(Collectors.toList());

    myNextListener.highlightingChanged(nextValues, PropagationDirection.FORWARD);
  }

  private void propagateBackward(@NotNull List<TraceElement> values) {
    final List<TraceElement> prevValues =
      values.stream().flatMap(x -> myResolvedTrace.getPreviousValues(x).stream()).collect(Collectors.toList());
    myPrevListener.highlightingChanged(prevValues, PropagationDirection.BACKWARD);
  }

  private void highlightAll(@NotNull List<TraceElement> values) {
    for (final TraceContainer listener : myListeners) {
      listener.highlight(values);
    }
  }

  private void selectAll(@NotNull List<TraceElement> values) {
    for (final TraceContainer listener : myListeners) {
      listener.select(values);
    }
  }
}
