package com.intellij.debugger.streams.trace.smart;

import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface TraceElement {
  int getTime();

  @NotNull
  Value getValue();

  @NotNull
  List<TraceElement> getNext();

  @NotNull
  List<TraceElement> getPrevious();

  @NotNull
  List<TraceElement> getPreviousBy(@NotNull TraceElement element);

  @NotNull
  List<TraceElement> getNextBy(@NotNull TraceElement element);
}
