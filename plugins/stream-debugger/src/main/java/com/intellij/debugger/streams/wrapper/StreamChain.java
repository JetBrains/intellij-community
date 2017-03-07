package com.intellij.debugger.streams.wrapper;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface StreamChain {
  @NotNull
  StreamCall getProducerCall();

  @NotNull
  List<StreamCall> getIntermediateCalls();

  @NotNull
  StreamCall getCall(int index);

  @NotNull
  StreamCall getTerminationCall();

  @NotNull
  String getText();

  int length();
}
