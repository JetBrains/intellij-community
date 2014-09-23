package com.jetbrains.python.debugger;

import com.jetbrains.python.debugger.pydev.AbstractCommand;
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
  private int myStopReason;
  private String myMessage;

  public PyThreadInfo(final String id,
                      final String name,
                      final List<PyStackFrameInfo> frames,
                      final int stopReason,
                      String message) {
    myId = id;
    myName = name;
    myFrames = (frames != null && frames.size() > 0 ? Collections.unmodifiableList(frames) : null);
    myStopReason = stopReason;
    myMessage = message;
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

  @Nullable
  public String getMessage() {
    return myMessage;
  }

  public void setMessage(@Nullable String message) {
    this.myMessage = message;
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


  public void setStopReason(int stopReason) {
    myStopReason = stopReason;
  }

  public int getStopReason() {
    return myStopReason;
  }

  public boolean isStopOnBreakpoint() {
    return myStopReason == AbstractCommand.SET_BREAKPOINT;
  }

  public boolean isExceptionBreak() {
    return myStopReason == AbstractCommand.ADD_EXCEPTION_BREAKPOINT
      || myStopReason == AbstractCommand.CMD_STEP_CAUGHT_EXCEPTION;
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
