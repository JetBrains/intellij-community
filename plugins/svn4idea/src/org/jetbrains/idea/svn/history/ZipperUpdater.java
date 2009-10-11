/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
