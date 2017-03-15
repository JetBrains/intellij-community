package com.intellij.debugger.streams.wrapper;

/**
 * @author Vitaliy.Bibaev
 */
public interface IntermediateStreamCall extends StreamCall {
  boolean hasPrimitiveSource();

  boolean hasPrimitiveResult();
}
