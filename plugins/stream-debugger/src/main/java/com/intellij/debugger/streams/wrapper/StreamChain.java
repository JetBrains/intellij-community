package com.intellij.debugger.streams.wrapper;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface StreamChain {
  @NotNull
  ProducerStreamCall getProducerCall();

  @NotNull
  List<IntermediateStreamCall> getIntermediateCalls();

  @NotNull
  StreamCall getCall(int index);

  @NotNull
  TerminatorStreamCall getTerminationCall();

  @NotNull
  String getText();

  int length();
}
