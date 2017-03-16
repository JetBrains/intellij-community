package com.intellij.debugger.streams.wrapper;

import com.intellij.debugger.streams.trace.impl.handler.type.GenericType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public interface TypeAfterAwareCall {
  @NotNull
  GenericType getTypeAfter();
}
