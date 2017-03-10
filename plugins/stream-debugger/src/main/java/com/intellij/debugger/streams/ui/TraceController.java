package com.intellij.debugger.streams.ui;

import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public interface TraceController {
  void register(@NotNull TraceContainer listener);
}
