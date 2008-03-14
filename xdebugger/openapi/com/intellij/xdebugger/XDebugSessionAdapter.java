package com.intellij.xdebugger;

/**
 * @author nik
 */
public abstract class XDebugSessionAdapter implements XDebugSessionListener {
  public void sessionPaused() {
  }

  public void sessionResumed() {
  }

  public void sessionStopped() {
  }

  public void stackFrameChanged() {
  }
}
