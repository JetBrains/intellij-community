package com.intellij.debugger.streams.trace.smart;

import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface TracingResult {
  @NotNull
  Value getResult();

  @NotNull
  List<TraceElement> getNestedItems();

  @NotNull
  List<TraceElement> getPrevious();

  @NotNull
  List<TraceElement> getNextBy(@NotNull TraceElement element);
}
