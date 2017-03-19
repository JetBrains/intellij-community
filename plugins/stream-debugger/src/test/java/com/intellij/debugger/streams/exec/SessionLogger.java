package com.intellij.debugger.streams.exec;

import com.intellij.xdebugger.XDebugSessionListener;

/**
 * @author Vitaliy.Bibaev
 */
public class SessionLogger implements XDebugSessionListener {
  @Override
  public void sessionPaused() {
    System.out.println("paused");
  }

  @Override
  public void sessionResumed() {
    System.out.println("resumed");

  }

  @Override
  public void sessionStopped() {
    System.out.println("stopped");
  }
}
