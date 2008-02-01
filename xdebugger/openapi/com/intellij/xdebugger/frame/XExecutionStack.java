package com.intellij.xdebugger.frame;

/**
 * @author nik
 */
public abstract class XExecutionStack {
  public static final XExecutionStack[] EMPTY_ARRAY = new XExecutionStack[0];

  public abstract XStackFrame[] getStackFrames();

}
