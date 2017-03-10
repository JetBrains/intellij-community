package com.intellij.debugger.streams.ui;

import com.intellij.debugger.streams.resolve.ResolvedTrace;
import com.intellij.debugger.streams.trace.smart.TraceElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;
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
      highlightAll(elements);
      final List<TraceElement> nextValues =
        elements.stream().flatMap(x -> myResolvedTrace.getNextValues(x).stream()).collect(Collectors.toList());
      final List<TraceElement> prevValues =
        elements.stream().flatMap(x -> myResolvedTrace.getPreviousValues(x).stream()).collect(Collectors.toList());
      myNextListener.highlightingChanged(nextValues, PropagationDirection.FORWARD);
      myPrevListener.highlightingChanged(prevValues, PropagationDirection.BACKWARD);
    };
  }

  void setPreviousListener(@NotNull ValuesHighlightingListener listener) {
    myPrevListener = listener;
  }

  void setNextListener(@NotNull ValuesHighlightingListener listener) {
    myNextListener = listener;
  }

  public List<TraceElement> getValues() {
    return myResolvedTrace.getValues();
  }

  @Override
  public void register(@NotNull TraceContainer listener) {
    myListeners.add(listener);
    listener.addSelectionListener(mySelectionListener);
  }

  @Override
  public void highlightingChanged(@NotNull List<TraceElement> values, @NotNull PropagationDirection direction) {
    highlightAll(values);

    if (direction.equals(PropagationDirection.BACKWARD)) {
      myPrevListener.highlightingChanged(values, PropagationDirection.BACKWARD);
    }
    else {
      myPrevListener.highlightingChanged(values, PropagationDirection.FORWARD);
    }
  }

  private void highlightAll(@NotNull List<TraceElement> values) {
    for (final TraceContainer listener : myListeners) {
      listener.highlight(values);
    }
  }
}
