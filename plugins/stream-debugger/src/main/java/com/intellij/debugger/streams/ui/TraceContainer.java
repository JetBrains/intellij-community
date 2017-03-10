package com.intellij.debugger.streams.ui;

import com.intellij.debugger.streams.trace.smart.TraceElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface TraceContainer {
  void highlight(@NotNull List<TraceElement> newSelection);

  void addSelectionListener(@NotNull ValuesSelectionListener listener);
}
