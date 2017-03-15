package com.intellij.debugger.streams.wrapper;

import com.intellij.debugger.streams.trace.smart.handler.type.GenericType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public interface TypeBeforeAwareCall {
  @NotNull
  GenericType getTypeBefore();
}
