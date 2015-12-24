package com.jetbrains.python.debugger;


import org.jetbrains.annotations.NotNull;

public class PyStackFrameInfo {

  private final String myThreadId;
  private final String myId;
  private final String myName;
  private final PySourcePosition myPosition;

  public PyStackFrameInfo(final String threadId, final String id, final String name, final PySourcePosition position) {
    myThreadId = threadId;
    myId = id;
    myName = name;
    myPosition = position;
  }

  @NotNull
  public String getThreadId() {
    return myThreadId;
  }

  public String getId() {
    return myId;
  }

  public String getName() {
    return myName;
  }

  public PySourcePosition getPosition() {
    return myPosition;
  }

}
