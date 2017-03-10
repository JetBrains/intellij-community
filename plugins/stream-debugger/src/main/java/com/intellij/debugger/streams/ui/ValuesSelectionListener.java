package com.intellij.debugger.streams.ui;

import com.intellij.debugger.streams.trace.smart.TraceElement;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface ValuesSelectionListener extends EventListener {
  void selectionChanged(@NotNull List<TraceElement> elements);
}
