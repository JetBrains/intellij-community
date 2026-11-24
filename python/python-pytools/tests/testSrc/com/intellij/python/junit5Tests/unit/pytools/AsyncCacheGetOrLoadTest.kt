// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.pytools

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.python.pytools.backend.getOrLoad
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger

/**
 * [getOrLoad] shares one load between the callers of a key, and a cancelled caller does not stop that load.
 *
 * `CompletionStage.await()` cancels the future when its caller is cancelled. With it, one cancelled caller stopped the
 * shared load, and all other callers got a `CancellationException`.
 */
@Suppress("RAW_SCOPE_CREATION")
internal class AsyncCacheGetOrLoadTest {
  private val cache: AsyncCache<String, String> = Caffeine.newBuilder().buildAsync()

  @Test
  fun `a cancelled caller does not stop the load for the other callers`(): Unit = timeoutRunBlocking {
    val loadScope = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext.job))
    val loads = AtomicInteger()
    val loadStarted = CompletableDeferred<Unit>()
    val gate = CompletableDeferred<Unit>()
    val load: suspend (String) -> String = { key ->
      loads.incrementAndGet()
      loadStarted.complete(Unit)
      gate.await()
      "value of $key"
    }

    val cancelledCaller = launch { cache.getOrLoad("key", loadScope, load) }
    loadStarted.await()
    val otherCaller = async { cache.getOrLoad("key", loadScope, load) }

    cancelledCaller.cancelAndJoin()
    gate.complete(Unit)

    assertEquals("value of key", otherCaller.await(), "the other caller must get the value of the shared load")
    assertEquals(1, loads.get(), "both callers must share one load")
    loadScope.cancel()
  }

  @Test
  fun `a failed load is not kept`(): Unit = timeoutRunBlocking {
    val loadScope = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext.job))
    val loads = AtomicInteger()
    val load: suspend (String) -> String = { key ->
      if (loads.incrementAndGet() == 1) throw IllegalStateException("first load fails")
      "value of $key"
    }

    val failure = try {
      cache.getOrLoad("key", loadScope, load)
      null
    }
    catch (e: IllegalStateException) {
      e
    }
    assertEquals("first load fails", failure?.message, "the caller must get the failure of the load")

    assertEquals("value of key", cache.getOrLoad("key", loadScope, load), "the next caller must start a new load")
    assertEquals(2, loads.get())
    loadScope.cancel()
  }

  @Test
  fun `a load that ended before the call gives its own exception`(): Unit = timeoutRunBlocking {
    // An unconfined load runs in the call, so its future is done before the caller waits for it.
    val loadScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob(coroutineContext.job))
    val load: suspend (String) -> String = { throw RefusedException() }

    val failure = try {
      cache.getOrLoad("key", loadScope, load)
      null
    }
    catch (e: CancellationException) {
      e
    }
    assertEquals(RefusedException::class.java, failure?.javaClass, "the caller must get the exception of the load")
    loadScope.cancel()
  }

  /** A refusal of the load, as a disposed key gives: a cancellation of its own type. */
  private class RefusedException : CancellationException("refused")
}
