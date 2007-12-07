package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ArrayList;

class ExecutionCallback {
  private boolean myExecuted;
  private List<Runnable> myRunnables;

  public void setExecuted() {
    myExecuted = true;
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
    if (myExecuted && myRunnables != null) {
      final java.lang.Runnable[] all = myRunnables.toArray(new java.lang.Runnable[myRunnables.size()]);
      myRunnables.clear();
      for (java.lang.Runnable each : all) {
        each.run();
      }
    }
  }

}
