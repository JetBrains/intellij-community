package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ArrayList;

class ExecutionCallback {
  private Executed myExecuted;
  private List<Runnable> myRunnables;

  ExecutionCallback() {
    myExecuted = new Executed(1);
  }

  ExecutionCallback(int executedCount) {
    myExecuted = new Executed(executedCount);
  }

  public void setExecuted() {
    myExecuted.signalExecution();
    callback();
  }

  final void doWhenExecuted(@NotNull final java.lang.Runnable runnable) {
    if (myRunnables == null) {
      myRunnables = new ArrayList<Runnable>();
    }

    myRunnables.add(runnable);

    callback();
  }

  final void notifyWhenExecuted(final ActionCallback child) {
    doWhenExecuted(new java.lang.Runnable() {
      public void run() {
        child.setDone();
      }
    });
  }

  private void callback() {
    if (myExecuted.isExecuted() && myRunnables != null) {
      final java.lang.Runnable[] all = myRunnables.toArray(new java.lang.Runnable[myRunnables.size()]);
      myRunnables.clear();
      for (java.lang.Runnable each : all) {
        each.run();
      }
    }
  }

  private class Executed {
    int myCurrentCount;
    int myCountToExecution;

    Executed(final int countToExecution) {
      myCountToExecution = countToExecution;
    }

    void signalExecution() {
      myCurrentCount++;
    }

    boolean isExecuted() {
      return myCurrentCount >= myCountToExecution;
    }
  }

}
