// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env

import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock


class LockSafeSemaphore(initialPermits: Int = 0): Semaphore(initialPermits) {
  private val semaphore = Semaphore(initialPermits)
  private val mutex = ReentrantLock()

  @Volatile
  var permits = initialPermits

  override fun acquire() {
    semaphore.acquire()
    mutex.lock()
    try {
      permits--
    }
    finally {
      mutex.unlock()
    }
  }

  override fun tryAcquire(timeout: Long, unit: TimeUnit): Boolean {
    val acquired = semaphore.tryAcquire(timeout, unit)
    mutex.lock()
    try {
      if (acquired) {
        permits--
      }
      return acquired
    }
    finally {
      mutex.unlock()
    }
  }

  override fun release() {
    semaphore.release()
    mutex.lock()
    try {
      permits++
    }
    finally {
      mutex.unlock()
    }
  }

  override fun availablePermits(): Int {
    mutex.lock()
    try {
      return permits
    }
    finally {
      mutex.unlock()
    }
  }
}