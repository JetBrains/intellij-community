// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;


import org.jetbrains.annotations.NotNull;

public class PyThreadEvent extends PyConcurrencyEvent {
  private final @NotNull String myParentThreadId;

  public PyThreadEvent(Long time, @NotNull String threadId, @NotNull String name, boolean isAsyncio) {
    super(time, threadId, name, isAsyncio);
    myParentThreadId = "";
  }

  public PyThreadEvent(long time, @NotNull String threadId, @NotNull String name, @NotNull String parentThreadId, boolean isAsyncio) {
    super(time, threadId, name, isAsyncio);
    myParentThreadId = parentThreadId;
  }

  @Override
  public @NotNull String getEventActionName() {
    return switch (myType) {
      case CREATE -> " created";
      case START -> " started";
      case JOIN -> " called join";
      case STOP -> " stopped";
      default -> " unknown command";
    };
  }

  public @NotNull String getParentThreadId() {
    return myParentThreadId;
  }

  @Override
  public boolean isThreadEvent() {
    return true;
  }

  @Override
  public String toString() {
    return myTime + " " + myThreadId + " PyThreadEvent" +
           " myType=" + myType +
           " " + myFileName +
           " " + myLine +
           "<br>";
  }
}
