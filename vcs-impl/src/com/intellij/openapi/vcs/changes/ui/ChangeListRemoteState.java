package com.intellij.openapi.vcs.changes.ui;

public class ChangeListRemoteState {
  // true -> ok
  private final boolean[] myChangeStates;

  public ChangeListRemoteState(final int size) {
    myChangeStates = new boolean[size];
    for (int i = 0; i < myChangeStates.length; i++) {
      myChangeStates[i] = true;
    }
  }

  public void report(final int idx, final boolean state) {
    myChangeStates[idx] = state;
  }

  public boolean getState() {
    boolean result = true;
    for (boolean state : myChangeStates) {
      result &= state;
    }
    return result;
  }
  
  public static class Reporter {
    private final int myIdx;
    private final ChangeListRemoteState myState;

    public Reporter(int idx, ChangeListRemoteState state) {
      myIdx = idx;
      myState = state;
    }

    public void report(final boolean state) {
      myState.report(myIdx, state);
    }
  }
}
