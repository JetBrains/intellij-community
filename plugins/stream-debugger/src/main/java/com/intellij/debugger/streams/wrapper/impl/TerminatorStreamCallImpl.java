package com.intellij.debugger.streams.wrapper.impl;

import com.intellij.debugger.streams.trace.smart.handler.type.GenericType;
import com.intellij.debugger.streams.wrapper.StreamCallType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class TerminatorStreamCallImpl extends StreamCallImpl implements com.intellij.debugger.streams.wrapper.TerminatorStreamCall {
  private final GenericType myTypeBefore;

  TerminatorStreamCallImpl(@NotNull String name, @NotNull String args, @NotNull GenericType typeBefore) {
    super(name, args, StreamCallType.TERMINATOR);
    myTypeBefore = typeBefore;
  }

  @NotNull
  @Override
  public GenericType getTypeBefore() {
    return myTypeBefore;
  }
}
