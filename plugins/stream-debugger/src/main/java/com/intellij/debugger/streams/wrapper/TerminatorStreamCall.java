package com.intellij.debugger.streams.wrapper;

/**
 * @author Vitaliy.Bibaev
 */
public interface TerminatorStreamCall extends StreamCall, TypeBeforeAwareCall {
  boolean isVoid();
}
