/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util;

import com.intellij.openapi.diagnostic.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ShutDownTracker implements Runnable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.ShutDownTracker");
  private static ShutDownTracker ourInstance;
  private List<Thread> myThreads = new ArrayList<Thread>();

  private ShutDownTracker() {
    Runtime.getRuntime().addShutdownHook(new Thread(this, "Shutdown tracker"));
  }

  public static synchronized ShutDownTracker getInstance() {
    if (ourInstance == null) {
      ourInstance = new ShutDownTracker();
    }
    return ourInstance;
  }

  public void run() {
    Thread[] threads = getStopperThreads();
    while (threads.length > 0) {
      Thread thread = threads[0];
      if (!thread.isAlive()) {
        if (isRegistered(thread)) {
          LOG.error("Thread '" + thread.getName() + "' did not unregister itself from ShutDownTracker.");
          unregisterStopperThread(thread);
        }
      }
      else {
        try {
          thread.join(100);
        }
        catch (InterruptedException e) {
        }
      }
      threads = getStopperThreads();
    }
  }

  private synchronized boolean isRegistered(Thread thread) {
    for (Iterator it = myThreads.iterator(); it.hasNext();) {
      Thread t = (Thread)it.next();
      if (t.equals(thread)) {
        return true;
      }
    }
    return false;
  }

  private synchronized Thread[] getStopperThreads() {
    return myThreads.toArray(new Thread[myThreads.size()]);
  }

  public synchronized void registerStopperThread(Thread thread) {
    myThreads.add(thread);
  }

  public synchronized void unregisterStopperThread(Thread thread) {
    myThreads.remove(thread);
  }

}
