
package com.jetbrains.python.debugger;


public class PyThreadEvent extends PyConcurrencyEvent {
  private String myParentThreadId;
  public PyThreadEvent(Long time, String threadId, String name, boolean isAsyncio) {
    super(time, threadId, name, isAsyncio);
  }

  public PyThreadEvent(long time, String threadId, String name, String parentThreadId, boolean isAsyncio) {
    super(time, threadId, name, isAsyncio);
    myParentThreadId = parentThreadId;
  }

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
