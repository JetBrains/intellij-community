package com.intellij.debugger.streams.wrapper;

import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public interface StreamCall extends MethodCall {
  @NotNull
  StreamCallType getType();
}
