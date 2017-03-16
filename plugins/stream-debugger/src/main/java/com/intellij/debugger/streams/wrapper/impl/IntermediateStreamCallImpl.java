package com.intellij.debugger.streams.wrapper.impl;

import com.intellij.debugger.streams.trace.impl.handler.type.GenericType;
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall;
import com.intellij.debugger.streams.wrapper.StreamCallType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class IntermediateStreamCallImpl extends StreamCallImpl implements IntermediateStreamCall {

  private final GenericType myTypeBefore;
  private final GenericType myTypeAfter;

  IntermediateStreamCallImpl(@NotNull String name,
                             @NotNull String args,
                             @NotNull GenericType typeBefore,
                             @NotNull GenericType typeAfter) {
    super(name, args, StreamCallType.INTERMEDIATE);
    myTypeBefore = typeBefore;
    myTypeAfter = typeAfter;
  }

  @NotNull
  @Override
  public GenericType getTypeBefore() {
    return myTypeBefore;
  }

  @NotNull
  @Override
  public GenericType getTypeAfter() {
    return myTypeAfter;
  }
}
