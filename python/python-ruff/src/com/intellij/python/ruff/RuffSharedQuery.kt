// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff

import com.intellij.openapi.diagnostic.logger
import com.jetbrains.python.NON_INTERACTIVE_ROOT_TRACE_CONTEXT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Runs one Ruff query at a time and shares it with every caller.
 *
 * [RuffService] needs this because a getter reads the cached Ruff information on every diagnostic
 * and on every completion item. A large project makes thousands of such reads before the first
 * query ends. Each read must join the query that runs now, or it starts another Ruff process.
 *
 * A query that loads the information runs one time only, until [invalidate] says the answer belongs
 * to a Ruff that no longer runs. A query that fails runs again, because the Python SDK of the
 * project can resolve after the first read. [retryDelay] and [maxAttempts] bound those retries, so
 * a broken Ruff cannot start a process for every read.
 *
 * The query never throws. A failure goes to the log.
 *
 * @param gate the lock that guards the state of the query. [RuffService] gives the lock of its cache,
 * so that one invalidation retires the answer and both queries in one step.
 * @param onLatch runs inside the latch while [gate] is held. A test blocks it to hold the query
 * between the displacement check and the write of the answer. Production code gives nothing.
 * @param query runs Ruff and loads the answer. It returns `true` when the information is there.
 */
@ApiStatus.Internal
class RuffSharedQuery(
  private val cs: CoroutineScope,
  private val retryDelay: Duration = 30.seconds,
  private val maxAttempts: Int = 5,
  private val gate: Any = Any(),
  private val onLatch: () -> Unit = {},
  private val query: suspend () -> Boolean,
) {
  private val current = AtomicReference<Job?>(null)
  private val attempts = AtomicInteger()

  @Volatile
  private var loaded = false

  @Volatile
  private var lastAttempt = 0L

  /**
   * Starts the query, or joins the query that runs now.
   *
   * @return the shared query. Join it to wait for the answer. Do not await it: [invalidate] cancels
   * the query, and a cancelled query must not cancel the caller that waits.
   */
  fun start(): Job {
    while (true) {
      val running = current.get()
      if (running != null && !retryable(running)) return running
      val candidate = cs.launch(NON_INTERACTIVE_ROOT_TRACE_CONTEXT, start = CoroutineStart.LAZY) {
        try {
          // Only the query that is still the current one can report an answer. [invalidate] can displace
          // this query after [query] returns, and cancellation does not stop a body with no suspension
          // point left. A displaced query that sets [loaded] spends the retry budget of its replacement,
          // and nothing retries it. So the check and the write run under [gate], which [invalidate] takes.
          if (query()) synchronized(gate) {
            if (current.get() === coroutineContext.job) {
              onLatch()
              loaded = true
            }
          }
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Throwable) {
          LOG.warn("Error querying Ruff", e)
        }
      }
      // The swap and the attempt count change together, so [invalidate] cannot reset the count between them.
      val swapped = synchronized(gate) {
        current.compareAndSet(running, candidate).also {
          if (it) {
            attempts.incrementAndGet()
            lastAttempt = System.nanoTime()
          }
        }
      }
      if (swapped) {
        candidate.start()
        return candidate
      }
      candidate.cancel()
    }
  }

  /**
   * Drops the loaded answer and the retry budget, so that the next [start] queries Ruff again. It also
   * cancels the query that runs now, because that query resolved the Ruff of the old configuration.
   *
   * The service that owns a query lives as long as the project, but the Ruff that answers can change
   * during that time. [RuffService] calls this when it learns of such a change.
   */
  fun invalidate() {
    retire()?.cancel()
  }

  /**
   * Does what [invalidate] does, but returns the displaced query instead of cancelling it. A caller
   * that retires more than one query under [gate] cancels them after it releases the lock.
   */
  fun retire(): Job? = synchronized(gate) {
    loaded = false
    attempts.set(0)
    current.getAndSet(null)
  }

  /**
   * Gives a query that has no answer a new retry budget. [retryDelay] still applies.
   *
   * A restarted LSP server is a new chance for a Ruff that failed before. A restart that reports the
   * same Ruff version does not reach [invalidate], so without this call the budget stays spent.
   */
  fun renewAttempts() {
    synchronized(gate) {
      if (!loaded) attempts.set(0)
    }
  }

  private fun retryable(running: Job): Boolean {
    if (loaded || !running.isCompleted) return false
    if (attempts.get() >= maxAttempts) return false
    return System.nanoTime() - lastAttempt >= retryDelay.inWholeNanoseconds
  }

  companion object {
    private val LOG = logger<RuffSharedQuery>()
  }
}
