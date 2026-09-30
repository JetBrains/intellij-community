// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.LongAdder

/**
 * Counts the work of the native Python code insight: type evaluations, type contexts, type matches, overload checks
 * and control flow builds.
 *
 * The counting is off by default. Then each hook costs one volatile read. A test turns the counting on with [enable],
 * runs an action, and compares two [snapshot] values. The hooks do not change a result.
 *
 * A counter in a [snapshot] is a sum over all threads. [countOnCurrentThread] counts the calling thread only.
 */
@ApiStatus.Internal
object PyCodeInsightCounters {

  /** A counter and its stable name in a [Snapshot]. */
  enum class Counter(val id: String) {
    GET_TYPE_CALLS("type.getType.calls"),
    GET_TYPE_CACHE_HITS("type.getType.cacheHits"),
    GET_TYPE_EVALUATIONS("type.getType.evaluations"),
    CONTEXTS_CONSTRUCTED("type.context.constructed"),
    CONTEXT_LOOKUP_MISSES("type.context.lookupMisses"),
    ASSUME_TYPE_CALLS("type.assumeType.calls"),
    MATCH_STEPS("type.match.steps"),
    OVERLOAD_CANDIDATES_CHECKED("overload.candidatesChecked"),
    CFG_BUILDS("cfg.builds"),
    CFG_INSTRUCTIONS("cfg.instructions"),
  }

  /** The counter values at one moment. */
  data class Snapshot(val counters: Map<String, Long>) {
    operator fun get(counter: Counter): Long = counters[counter.id] ?: 0

    operator fun minus(before: Snapshot): Snapshot =
      Snapshot(counters.mapValues { (key, value) -> value - (before.counters[key] ?: 0) })
  }

  @Volatile
  private var enabled = false
  private val enablerLock = Any()
  private var enablers = 0
  private val counters = Array(Counter.entries.size) { LongAdder() }
  private val threadCounts = ThreadLocal<LongArray?>()

  @JvmStatic
  val isEnabled: Boolean
    get() = enabled

  @JvmStatic
  fun inc(counter: Counter) {
    if (enabled) add(counter, 1)
  }

  @JvmStatic
  fun add(counter: Counter, value: Long) {
    if (!enabled) return
    counters[counter.ordinal].add(value)
    threadCounts.get()?.let { it[counter.ordinal] += value }
  }

  @JvmStatic
  fun snapshot(): Snapshot = Snapshot(Counter.entries.associate { it.id to counters[it.ordinal].sum() })

  /**
   * Runs [action] with the counting on and returns the counter values of the calling thread only.
   * The work of other threads does not count. A nested call also adds its counts to the outer call.
   */
  @TestOnly
  @JvmStatic
  fun countOnCurrentThread(action: () -> Unit): Map<Counter, Long> {
    val outer = threadCounts.get()
    val counts = LongArray(Counter.entries.size)
    val disposable = Disposer.newDisposable("PyCodeInsightCounters.countOnCurrentThread")
    enable(disposable)
    threadCounts.set(counts)
    try {
      action()
    }
    finally {
      threadCounts.set(outer)
      Disposer.dispose(disposable)
    }
    if (outer != null) {
      for (index in counts.indices) outer[index] += counts[index]
    }
    return Counter.entries.associateWith { counts[it.ordinal] }
  }

  /** Turns the counting on until [parentDisposable] is disposed. */
  @TestOnly
  @JvmStatic
  fun enable(parentDisposable: Disposable) {
    synchronized(enablerLock) {
      enablers++
      enabled = true
    }
    Disposer.register(parentDisposable) {
      synchronized(enablerLock) {
        enablers--
        enabled = enablers > 0
      }
    }
  }
}
