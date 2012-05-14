/*
 * Copyright (c) 2003, 2010, Dave Kriewall
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1) Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.wrq.rearranger;

/**
 * Class to permit a thread to wait until another thread has finished work and set the boolean
 * to true.
 */
public final class WaitableBoolean {
// ------------------------------ FIELDS ------------------------------

  private       boolean value_;
  private final Object  lock_;

// --------------------------- CONSTRUCTORS ---------------------------

  /**
   * Make a new WaitableBoolean with the given initial value,
   * and using its own internal lock.
   */
  public WaitableBoolean() {
    lock_ = this;
    value_ = false;
  }

// -------------------------- OTHER METHODS --------------------------

  public final void set() {
    set(true);
  }

  /** Set to newValue. */

  public final void set(final boolean value) {
    synchronized (lock_) {
      lock_.notifyAll();
      value_ = value;
    }
  }

  /**
   * wait until value is true, then run action if nonnull.
   * The action is run with the synchronization lock held.
   */
  public final void whenTrue() throws InterruptedException {
    synchronized (lock_) {
      while (!value_) lock_.wait();
    }
  }
}
