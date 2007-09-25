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

import gnu.trove.TIntArrayList;

import java.util.ArrayList;

/**
 * A writer-preference ReadWriteLock that allows both readers and
 * writers to reacquire
 * read or write locks in the style of a ReentrantLock.
 * Readers are not allowed until all write locks held by
 * the writing thread have been released.
 * Among other applications, reentrancy can be useful when
 * write locks are held during calls or callbacks to methods that perform
 * reads under read locks.
 * <p>
 * <b>Sample usage</b>. Here is a code sketch showing how to exploit
 * reentrancy to perform lock downgrading after updating a cache:
 * <pre>
 * class CachedData {
 *   Object data;
 *   volatile boolean cacheValid;
 *   ReentrantWriterPreferenceReadWriteLock rwl = ...
 *
 *   void processCachedData() {
 *     rwl.readLock().acquire();
 *     if (!cacheValid) {
 *
 *        // upgrade lock:
 *        rwl.readLock().release();   // must release first to obtain writelock
 *        rwl.writeLock().acquire();
 *        if (!cacheValid) { // recheck
 *          data = ...
 *          cacheValid = true;
 *        }
 *        // downgrade lock
 *        rwl.readLock().acquire();  // reacquire read without giving up lock
 *        rwl.writeLock().release(); // release write, still hold read
 *     }
 *
 *     use(data);
 *     rwl.readLock().release();
 *   }
 * }
 * </pre>
 *
 *
 * <p>[<a href="http://gee.cs.oswego.edu/dl/classes/EDU/oswego/cs/dl/util/concurrent/intro.html"> Introduction to this package. </a>]
 * @see ReentrantLock
 **/

public class ReentrantWriterPreferenceReadWriteLock extends WriterPreferenceReadWriteLock {

  /** Number of acquires on write lock by activeWriter_ thread **/
  protected long writeHolds_ = 0;

  protected ThreadToCountMap readers_ = new ThreadToCountMap();

  public synchronized boolean isReadLockAcquired(Thread thread){
    return readers_.get(thread) > 0;
  }

  public synchronized boolean isWriteLockAcquired(Thread thread){
    return activeWriter_ == thread;
  }

  protected boolean allowReader() {
    // [Valentin] Changed policy so that readers are allowed while there are waiting writers
    // [cdr]: No more!
    return (activeWriter_ == null && waitingWriters_ == 0) ||
      activeWriter_ == Thread.currentThread();
  }

  protected synchronized boolean startRead() {
    Thread t = Thread.currentThread();
    int c = readers_.get(t);
    if (c > 0) { // already held -- just increment hold count
      readers_.put(t, c + 1);
      ++activeReaders_;
      return true;
    }
    else if (allowReader()) {
      readers_.put(t, 1);
      ++activeReaders_;
      return true;
    }
    else
      return false;
  }

  protected synchronized boolean startWrite() {
    if (activeWriter_ == Thread.currentThread()) { // already held; re-acquire
      ++writeHolds_;
      return true;
    }
    else if (writeHolds_ == 0) {
      if (activeReaders_ == 0 ||
          (readers_.size() == 1 &&
           readers_.get(Thread.currentThread()) > 0)) {
        activeWriter_ = Thread.currentThread();
        writeHolds_ = 1;
        return true;
      }
      else
        return false;
    }
    else
      return false;
  }


  protected synchronized Signaller endRead() {
    --activeReaders_;
    Thread t = Thread.currentThread();
    int c = readers_.get(t);
    if (c != 1) { // more than one hold; decrement count
      readers_.put(t, c - 1);
      return null;
    }
    else {
      readers_.put(t, 0);

      if (writeHolds_ > 0) { // a write lock is still held by current thread
        return null;
      }
      else if (/*activeReaders_ == 0 && */activeReaders_ <= 1 && waitingWriters_ > 0) {
        // [Valentin] commented out check for activeReaders == 0 - it's incorrect when waiting writer is already a reader!!
        return writerLock_;
      }
      else{
        return null;
      }
    }
  }

  protected synchronized Signaller endWrite() {
    --writeHolds_;
    if (writeHolds_ > 0)   // still being held
      return null;
    else {
      activeWriter_ = null;
      if (waitingReaders_ > 0 && allowReader())
        return readerLock_;
      else if (waitingWriters_ > 0)
        return writerLock_;
      else
        return null;
    }
  }

  private static final class ThreadToCountMap{
    private ArrayList<Thread> myThreads = new ArrayList<Thread>();
    private TIntArrayList myCounters = new TIntArrayList();

    private Thread myLastThread = null; // optimization
    private int myLastCounter;

    public int get(Thread thread){
      if (thread == myLastThread) return myLastCounter;
      int index = myThreads.indexOf(thread);
      int result = index >= 0 ? myCounters.getQuick(index) : 0;
      myLastThread = thread;
      myLastCounter = result;
      return result;
    }

    public void put(Thread thread, int count){
      myLastThread = null;
      int index = myThreads.indexOf(thread);
      if (index >= 0){
        if (count == 0){
          myThreads.remove(index);
          myCounters.remove(index);
        }
        else{
          myCounters.setQuick(index, count);
        }
      }
      else{
        if (count != 0){
          myThreads.add(thread);
          myCounters.add(count);
        }
      }
    }

    public int size(){
      return myThreads.size();
    }
  }
}

