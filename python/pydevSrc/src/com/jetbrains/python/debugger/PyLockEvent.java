// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.jetbrains.python.debugger;


import org.jetbrains.annotations.NotNull;

public class PyLockEvent extends PyConcurrencyEvent {
  private final @NotNull String myLockId;

  public PyLockEvent(long time, @NotNull String threadId, @NotNull String name, @NotNull String id, boolean isAsyncio) {
    super(time, threadId, name, isAsyncio);
    myLockId = id;
  }

  @Override
  public @NotNull String getEventActionName() {
    return switch (myType) {
      case CREATE -> " created";
      case ACQUIRE_BEGIN -> " acquire started";
      case ACQUIRE_END -> " acquired";
      case RELEASE -> " released";
      default -> " unknown command";
    };
  }

  public @NotNull String getLockId() {
    return myLockId;
  }

  @Override
  public boolean isThreadEvent() {
    return false;
  }

  @Override
  public String toString() {
    return myTime + " " + myThreadId + " PyLockEvent" +
           " myType=" + myType +
           " myLockId= " + myLockId +
           " " + myFileName +
           " " + myLine +
           "<br>";
  }
}
