package org.jetbrains.plugins.textmate.concurrent

import kotlin.concurrent.withLock

internal fun <T> createTextMateThreadLocalJvm(): TextMateThreadLocal<T> {
  return object : TextMateThreadLocal<T> {
    private val threadLocal = ThreadLocal<T>()

    override fun get(): T {
      return threadLocal.get()
    }

    override fun set(value: T) {
      threadLocal.set(value)
    }
  }
}

internal fun createTextMateLockJvm(): TextMateLock = object : TextMateLock {
  val lock = java.util.concurrent.locks.ReentrantLock()

  override fun <T> withLock(body: () -> T): T {
    return lock.withLock(body)
  }
}