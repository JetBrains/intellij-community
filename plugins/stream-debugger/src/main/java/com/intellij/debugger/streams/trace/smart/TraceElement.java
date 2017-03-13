package com.intellij.debugger.streams.trace.smart;

import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public interface TraceElement {
  int getTime();

  @NotNull
  Value getValue();
}
