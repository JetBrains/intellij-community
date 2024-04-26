// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.jetbrains.python.debugger;


import org.jetbrains.annotations.NotNull;

public class PyLockEvent extends PyConcurrencyEvent {
  private final @NotNull String myLockId;

  public PyLockEvent(long time, @NotNull String threadId, @NotNull String name, @NotNull String id, boolean isAsyncio) {
    super(time, threadId, name, isAsyncio);
    myLockId = id;
  }

  @NotNull
  @Override
  public String getEventActionName() {
    return switch (myType) {
      case CREATE -> " created";
      case ACQUIRE_BEGIN -> " acquire started";
      case ACQUIRE_END -> " acquired";
      case RELEASE -> " released";
      default -> " unknown command";
    };
  }

  @NotNull
  public String getLockId() {
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
