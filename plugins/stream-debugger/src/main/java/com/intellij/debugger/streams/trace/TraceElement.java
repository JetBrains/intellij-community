package com.intellij.debugger.streams.trace;

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
}
