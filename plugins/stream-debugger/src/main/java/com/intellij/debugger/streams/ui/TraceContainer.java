package com.intellij.debugger.streams.ui;

import com.intellij.debugger.streams.trace.TraceElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface TraceContainer {
  void highlight(@NotNull List<TraceElement> elements);

  void select(@NotNull List<TraceElement> elements);

  void addSelectionListener(@NotNull ValuesSelectionListener listener);
}
