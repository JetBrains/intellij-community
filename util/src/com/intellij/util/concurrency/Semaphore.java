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
package com.intellij.util.concurrency;

import com.intellij.openapi.diagnostic.Logger;

public class Semaphore {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.concurrency.Semaphore");
  private int mySemaphore = 0;

  public synchronized void down() {
    mySemaphore++;
  }

  public synchronized void up() {
    mySemaphore--;
    if (mySemaphore == 0) {
      notifyAll();
    }
  }

  public synchronized void waitFor() {
    try {
      while (mySemaphore > 0) {
        wait();
      }
    }
    catch (InterruptedException e) {
      LOG.debug(e);
      throw new RuntimeException(e);
    }
  }

  public synchronized void waitFor(final long timeout) {
    try {
      final long startTime = System.currentTimeMillis();
      long waitTime = timeout;
      while (mySemaphore > 0) {
        wait(waitTime);
        final long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed < timeout) {
          waitTime = timeout - elapsed;
        }
        else {
          break;
        }
      }
    }
    catch (InterruptedException e) {
      LOG.debug(e);
      throw new RuntimeException(e);
    }
  }

}
