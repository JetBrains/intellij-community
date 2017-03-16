package com.intellij.debugger.streams.ui;

import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.debugger.streams.wrapper.StreamCall;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface TraceController {
  @NotNull
  List<TraceElement> getValues();

  @NotNull
  StreamCall getCall();

  void register(@NotNull TraceContainer listener);
}
