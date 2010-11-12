package com.jetbrains.python.debugger;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;


public class PyThreadInfo {

  public enum State {
    RUNNING, SUSPENDED, KILLED
  }

  private final String myId;
  private final String myName;
  private List<PyStackFrameInfo> myFrames;
  private State myState;
  private boolean myStopOnBreakpoint;
  private String myLogExpressionMessage;

  public void setStopOnBreakpoint(boolean stopOnBreakpoint) {
    myStopOnBreakpoint = stopOnBreakpoint;
  }

  public PyThreadInfo(final String id, final String name, final List<PyStackFrameInfo> frames, final boolean stopOnBreakpoint, String logExpressionMessage) {
    myId = id;
    myName = name;
    myFrames = (frames != null && frames.size() > 0 ? Collections.unmodifiableList(frames) : null);
    myStopOnBreakpoint = stopOnBreakpoint;
    myLogExpressionMessage = logExpressionMessage;
  }

  public String getId() {
    return myId;
  }

  public boolean isPydevThread() {
    return "-1".equals(myId);
  }

  public String getName() {
    return myName;
  }

  public String getLogExpressionMessage() {
    return myLogExpressionMessage;
  }

  public void setLogExpressionMessage(String logExpressionMessage) {
    myLogExpressionMessage = logExpressionMessage;
  }

  @Nullable
  public synchronized List<PyStackFrameInfo> getFrames() {
    return myFrames;
  }

  public synchronized State getState() {
    return myState;
  }

  public synchronized void updateState(final State state, final List<PyStackFrameInfo> frames) {
    myState = state;
    myFrames = (frames != null && frames.size() > 0 ? Collections.unmodifiableList(frames) : null);
  }

  public boolean isStopOnBreakpoint() {
    return myStopOnBreakpoint;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PyThreadInfo that = (PyThreadInfo)o;

    if (myId != null ? !myId.equals(that.myId) : that.myId != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myId != null ? myId.hashCode() : 0;
  }
}
