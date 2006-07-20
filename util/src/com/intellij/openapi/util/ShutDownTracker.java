/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.util;

import com.intellij.openapi.diagnostic.Logger;

import java.util.ArrayList;
import java.util.List;

public class ShutDownTracker implements Runnable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.ShutDownTracker");
  private static ShutDownTracker ourInstance;
  private List<Thread> myThreads = new ArrayList<Thread>();
  private List<Thread> myShutdownTreads = new ArrayList<Thread>();

  private ShutDownTracker() {
    //noinspection HardCodedStringLiteral
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

    while (myShutdownTreads.size() > 0) {
      final Thread thread = myShutdownTreads.remove(0);
      thread.start();
      try {
        thread.join();
      }
      catch (InterruptedException e) { }
    }
  }

  private synchronized boolean isRegistered(Thread thread) {
    for (final Thread myThread : myThreads) {
      Thread t = (Thread)myThread;
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

  public void registerShutdownThread(final Thread thread) {
    myShutdownTreads.add(thread);
  }

  public void registerShutdownThread(int index, final Thread thread) {
    myShutdownTreads.add(index, thread);
  }
}
