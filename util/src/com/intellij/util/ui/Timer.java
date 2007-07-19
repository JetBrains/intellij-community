package com.intellij.util.ui;

import com.intellij.openapi.Disposable;

public abstract class Timer implements Disposable {

  private Thread myThread;
  private int mySpan;

  private boolean myRunning;
  private boolean myDisposed;
  private boolean myRestartRequest;

  private String myName;

  public Timer(String name, int span) {
    myName = name;
    mySpan = span;
    myThread = new Thread(name) {
      public void run() {
        try {
          while(true) {
            sleep(mySpan);

            if (myRestartRequest) {
              myRestartRequest = false;
              continue;
            }

            if (myRunning) {
              onTimer();
            }
          }
        } catch (InterruptedException e) {
        }
        myDisposed = true;
      }
    };
  }

  public final int getSpan() {
    return mySpan;
  }

  public final void start() {
    assert !myThread.isAlive();
    myThread.start();
  }

  protected abstract void onTimer() throws InterruptedException;

  public final void suspend() {
    if (myDisposed) return;

    if (myThread.isAlive()) {
      myRunning = false;
    }
  }

  public final void resume() {
    startIfNeeded();
    myRunning = true;
  }

  private void startIfNeeded() {
    if (myDisposed) return;

    if (!myThread.isAlive()) {
      start();
    }
  }

  public final void dispose() {
    if (myThread.isAlive()) {
      myThread.interrupt();
      myDisposed = true;
    }
  }

  public void restart() {
    startIfNeeded();
    myRestartRequest = true;
  }

  public boolean isTimerThread() {
    return Thread.currentThread() == myThread;
  }

  public String toString() {
    return "Timer=" + myName;
  }

  public boolean isRunning() {
    return myRunning;
  }

  public boolean isDisposed() {
    return myDisposed;
  }
}
