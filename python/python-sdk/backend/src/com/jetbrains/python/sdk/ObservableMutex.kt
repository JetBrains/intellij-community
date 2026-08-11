// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.jetbrains.python.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import org.jetbrains.annotations.ApiStatus

/**
 * A [Mutex] wrapper that exposes lock state as an observable [StateFlow].
 */
@ApiStatus.Internal
class ObservableMutex {
  private val delegate = Mutex()
  private val _isLocked = MutableStateFlow(false)

  /** Observable lock state. `true` while the mutex is held. */
  val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

  /** Acquires the lock, runs [action], and releases the lock. Suspends if already held. */
  suspend fun <T> withLock(action: suspend () -> T): T {
    delegate.lock()
    try {
      _isLocked.value = true
      return action()
    }
    finally {
      _isLocked.value = false
      delegate.unlock()
    }
  }

  /**
   * Runs [action] under the lock if it's not already held.
   * Returns [Result.Failure] with [AlreadyLocked] if the lock is busy.
   */
  suspend fun <T> tryWithLock(action: suspend () -> T): Result<T, AlreadyLocked> {
    if (!delegate.tryLock()) return Result.failure(AlreadyLocked)
    try {
      _isLocked.value = true
      return Result.success(action())
    }
    finally {
      _isLocked.value = false
      delegate.unlock()
    }
  }

  /** Sentinel error returned by [tryWithLock] when the lock is already held. */
  data object AlreadyLocked
}
