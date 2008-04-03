package org.jetbrains.idea.svn.config;

public class CompositeRunnable implements Runnable {
  private final Runnable[] myRunnables;

  public CompositeRunnable(final Runnable... runnables) {
    myRunnables = runnables;
  }

  public void run() {
    for (Runnable runnable : myRunnables) {
      runnable.run();
    }
  }
}
