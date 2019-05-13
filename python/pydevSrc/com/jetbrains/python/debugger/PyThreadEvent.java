
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

  @NotNull
  @Override
  public String getEventActionName() {
    StringBuilder sb = new StringBuilder();
    switch (myType) {
      case CREATE:
        sb.append(" created");
        break;
      case START:
        sb.append(" started");
        break;
      case JOIN:
        sb.append(" called join");
        break;
      case STOP:
        sb.append(" stopped");
        break;
      default:
        sb.append(" unknown command");
    }
    return sb.toString();
  }

  @NotNull
  public String getParentThreadId() {
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
