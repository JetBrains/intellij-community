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

/**
 *  ReadWriteLocks maintain a pair of associated locks.
 * The readLock may be held simultanously by multiple
 * reader threads, so long as there are no writers. The writeLock
 * is exclusive. ReadWrite locks are generally preferable to
 * plain Sync locks or synchronized methods in cases where:
 * <ul>
 *   <li> The methods in a class can be cleanly separated into
 *        those that only access (read) data vs those that 
 *        modify (write).
 *   <li> Target applications generally have more readers than writers.
 *   <li> The methods are relatively time-consuming (as a rough
 *        rule of thumb, exceed more than a hundred instructions), so it
 *        pays to introduce a bit more overhead associated with
 *        ReadWrite locks compared to simple synchronized methods etc
 *        in order to allow concurrency among reader threads.
 *        
 * </ul>
 * Different implementation classes differ in policies surrounding
 * which threads to prefer when there is
 * contention. By far, the most commonly useful policy is 
 * WriterPreferenceReadWriteLock. The other implementations
 * are targeted for less common, niche applications.
 *<p>
 * Standard usage:
 * <pre>
 * class X {
 *   ReadWriteLock rw;
 *   // ...
 *
 *   public void read() throws InterruptedException { 
 *     rw.readLock().acquire();
 *     try {
 *       // ... do the read
 *     }
 *     finally {
 *       rw.readlock().release()
 *     }
 *   }
 *
 *
 *   public void write() throws InterruptedException { 
 *     rw.writeLock().acquire();
 *     try {
 *       // ... do the write
 *     }
 *     finally {
 *       rw.writelock().release()
 *     }
 *   }
 * }
 * </pre>
 * @see Sync
 * <p>[<a href="http://gee.cs.oswego.edu/dl/classes/EDU/oswego/cs/dl/util/concurrent/intro.html"> Introduction to this package. </a>]

**/

public interface ReadWriteLock {
  /** get the readLock **/
  Sync readLock();

  /** get the writeLock **/
  Sync writeLock();
}

