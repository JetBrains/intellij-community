package com.jetbrains.python.debugger;

public interface PyFrameListener {

  void frameChanged();

  default void sessionStopped() {}
}
