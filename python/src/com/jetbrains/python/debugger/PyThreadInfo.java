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
  private final boolean myStopOnBreakpoint;  // todo: remove

  public PyThreadInfo(final String id, final String name, final List<PyStackFrameInfo> frames, final boolean stopOnBreakpoint) {
    myId = id;
    myName = name;
    myFrames = (frames != null && frames.size() > 0 ? Collections.unmodifiableList(frames) : null);
    myStopOnBreakpoint = stopOnBreakpoint;
  }

  public String getId() {
    return myId;
  }

  public String getName() {
    return myName;
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

}
