package com.intellij.debugger.streams.wrapper.impl;

import com.intellij.debugger.streams.trace.impl.handler.type.GenericType;
import com.intellij.debugger.streams.wrapper.StreamCallType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class TerminatorStreamCallImpl extends StreamCallImpl implements com.intellij.debugger.streams.wrapper.TerminatorStreamCall {
  private final GenericType myTypeBefore;
  private final boolean myIsVoid;

  TerminatorStreamCallImpl(@NotNull String name, @NotNull String args, @NotNull GenericType typeBefore, boolean isVoid) {
    super(name, args, StreamCallType.TERMINATOR);
    myTypeBefore = typeBefore;
    myIsVoid = isVoid;
  }

  @NotNull
  @Override
  public GenericType getTypeBefore() {
    return myTypeBefore;
  }

  @Override
  public boolean isVoid() {
    return myIsVoid;
  }
}
