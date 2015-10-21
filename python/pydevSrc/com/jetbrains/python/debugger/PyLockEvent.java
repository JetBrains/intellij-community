
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
    StringBuilder sb = new StringBuilder();
    switch (myType) {
      case CREATE:
        sb.append(" created");
        break;
      case ACQUIRE_BEGIN:
        sb.append(" acquire started");
        break;
      case ACQUIRE_END:
        sb.append(" acquired");
        break;
      case RELEASE:
        sb.append(" released");
        break;
      default:
        sb.append(" unknown command");
    }
    return sb.toString();
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
