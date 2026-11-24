// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.backend

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * One [load] that its callers share. The value of a load stays for [ttl] after the load ends.
 *
 * A caller of [get] that starts a load gives its argument to the load. A caller that shares a running load or takes a
 * kept value gets the value of a load that the argument of another caller started.
 *
 * The load runs on [scope], so a cancelled caller stops to wait, and the load continues for the other callers. A load
 * that fails or is cancelled is not kept, and the next caller starts a new load. [scope] must be a supervisor scope,
 * such as the scope of a service, because a failed load must not cancel it.
 */
@ApiStatus.Internal
class ExpiringSharedLoad<A, T>(
  private val scope: CoroutineScope,
  private val ttl: Duration,
  private val timeSource: TimeSource = TimeSource.Monotonic,
  private val load: suspend (A) -> T,
) {
  private class Entry<T>(val deferred: Deferred<T>) {
    @Volatile
    var ended: TimeMark? = null
  }

  private val current = AtomicReference<Entry<T>?>()

  /**
   * The value of the running load, or of the last load when it ended less than [ttl] ago. Else starts a load of
   * [argument].
   */
  suspend fun get(argument: A): T = share(argument).await()

  /**
   * Starts or shares the load as [get] does, but does not wait for it. A caller can so start the load under a lock or
   * a read action, and wait for it after.
   */
  fun share(argument: A): Deferred<T> = entry(argument).deferred

  /**
   * Forgets the current load, for a caller that has just changed what a load finds. A running load continues for the
   * callers that wait for it, and the next caller starts a new load.
   */
  fun invalidate() {
    current.set(null)
  }

  /** Cancels the current load and forgets it. The callers that wait for it get a [CancellationException]. */
  fun cancel() {
    current.getAndSet(null)?.deferred?.cancel()
  }

  private fun entry(argument: A): Entry<T> {
    while (true) {
      val existing = current.get()
      if (existing != null && !existing.isExpired()) return existing
      val entry = Entry(scope.async(start = CoroutineStart.LAZY) { load(argument) })
      if (current.compareAndSet(existing, entry)) {
        entry.deferred.invokeOnCompletion { error ->
          // After invalidate() a newer load can be current. A failure of the older load must not forget the newer one.
          if (error == null) entry.ended = timeSource.markNow() else current.compareAndSet(entry, null)
        }
        entry.deferred.start()
        return entry
      }
      // Another caller started a load first. This one did not start, and the loop takes the load of the other caller.
      entry.deferred.cancel()
    }
  }

  private fun Entry<T>.isExpired(): Boolean = ended?.let { it.elapsedNow() >= ttl } ?: false
}
