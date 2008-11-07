package org.jetbrains.idea.svn.history;

import com.intellij.util.Alarm;

public class ZipperUpdater {
  private final Alarm myAlarm;
  private boolean myRaised;
  private final Object myLock = new Object();
  private final static int DELAY = 300;

  public ZipperUpdater() {
    myAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
  }

  public void queue(final Runnable runnable) {
    synchronized (myLock) {
      myRaised = true;
    }
    myAlarm.addRequest(new Runnable() {
      public void run() {
        synchronized (myLock) {
          if (! myRaised) return;
          myRaised = false;
        }
        runnable.run();
      }
    }, DELAY);
  }
}
