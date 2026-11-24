// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.pytools

import com.intellij.python.pytools.backend.ExpiringSharedLoad
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/**
 * [ExpiringSharedLoad] shares one load between its callers and keeps the value of the load for the TTL.
 */
@Suppress("RAW_SCOPE_CREATION")
internal class ExpiringSharedLoadTest {
  private val loads = AtomicInteger()
  private val time = TestTimeSource()

  /** Completes when the first load starts. */
  private val firstStarted = CompletableDeferred<Unit>()

  /** The first load waits for it. The loads after the first one do not wait. */
  private val firstGate = CompletableDeferred<Unit>()

  private fun CoroutineScope.loadScope(): CoroutineScope =
    CoroutineScope(coroutineContext + SupervisorJob(coroutineContext.job))

  /** A load that gives "value N" for the load number N. When [gated], the first load waits for [firstGate]. */
  private fun sharedLoad(
    scope: CoroutineScope,
    gated: Boolean = false,
    failFirst: Boolean = false,
  ): ExpiringSharedLoad<Unit, String> =
    ExpiringSharedLoad(scope, TTL, time) {
      val number = loads.incrementAndGet()
      if (number == 1) {
        firstStarted.complete(Unit)
        if (gated) firstGate.await()
        if (failFirst) throw IllegalStateException("the first load fails")
      }
      "value $number"
    }

  @Test
  fun `concurrent callers share one load`(): Unit = timeoutRunBlocking {
    val scope = loadScope()
    val shared = sharedLoad(scope, gated = true)

    val callers = List(8) { async { shared.get(Unit) } }
    firstStarted.await()
    firstGate.complete(Unit)

    assertEquals(List(8) { "value 1" }, callers.awaitAll())
    assertEquals(1, loads.get())
    scope.cancel()
  }

  @Test
  fun `the value stays for the TTL and expires after it`(): Unit = timeoutRunBlocking {
    val scope = loadScope()
    val shared = sharedLoad(scope)

    assertEquals("value 1", shared.get(Unit))
    time += TTL - 1.seconds
    assertEquals("value 1", shared.get(Unit), "the value must stay before the TTL ends")
    time += 1.seconds
    assertEquals("value 2", shared.get(Unit), "the value must expire when the TTL ends")
    scope.cancel()
  }

  @Test
  fun `a cancelled caller does not stop the load for the other callers`(): Unit = timeoutRunBlocking {
    val scope = loadScope()
    val shared = sharedLoad(scope, gated = true)

    val cancelledCaller = launch { shared.get(Unit) }
    firstStarted.await()
    val otherCaller = async { shared.get(Unit) }
    cancelledCaller.cancelAndJoin()
    firstGate.complete(Unit)

    assertEquals("value 1", otherCaller.await())
    assertEquals(1, loads.get())
    scope.cancel()
  }

  @Test
  fun `a failed load is not kept`(): Unit = timeoutRunBlocking {
    val scope = loadScope()
    val shared = sharedLoad(scope, failFirst = true)

    assertEquals("the first load fails", illegalStateOf { shared.get(Unit) }.message)
    assertEquals("value 2", shared.get(Unit), "the next caller must start a new load")
    scope.cancel()
  }

  @Test
  fun `invalidate starts a new load and the running load continues for its callers`(): Unit = timeoutRunBlocking {
    val scope = loadScope()
    val shared = sharedLoad(scope, gated = true)

    val firstCaller = async { shared.get(Unit) }
    firstStarted.await()
    shared.invalidate()
    assertEquals("value 2", shared.get(Unit), "a caller after invalidate() must get a new load")
    firstGate.complete(Unit)

    assertEquals("value 1", firstCaller.await(), "the running load must continue for its callers")
    assertEquals("value 2", shared.get(Unit), "the older load must not replace the newer one")
    scope.cancel()
  }

  @Test
  fun `a failure of an older load does not forget the newer load`(): Unit = timeoutRunBlocking {
    val scope = loadScope()
    val shared = sharedLoad(scope, gated = true, failFirst = true)

    val firstCaller = async { illegalStateOf { shared.get(Unit) } }
    firstStarted.await()
    shared.invalidate()
    assertEquals("value 2", shared.get(Unit))
    firstGate.complete(Unit)
    firstCaller.await()

    assertEquals("value 2", shared.get(Unit), "the newer load must stay")
    assertEquals(2, loads.get())
    scope.cancel()
  }

  @Test
  fun `cancel stops the running load and the next caller starts a new load`(): Unit = timeoutRunBlocking {
    val scope = loadScope()
    val shared = sharedLoad(scope, gated = true)

    val firstCaller = async { cancellationOf { shared.get(Unit) } }
    firstStarted.await()
    shared.cancel()

    firstCaller.await()
    assertEquals("value 2", shared.get(Unit))
    scope.cancel()
  }

  @Test
  fun `a cancelled scope gives a cancellation and no load`(): Unit = timeoutRunBlocking {
    val scope = loadScope()
    val shared = sharedLoad(scope)
    scope.cancel()

    cancellationOf { shared.get(Unit) }
    assertEquals(0, loads.get())
  }

  private suspend fun illegalStateOf(block: suspend () -> Unit): IllegalStateException =
    try {
      block()
      fail("expected an IllegalStateException")
    }
    catch (e: IllegalStateException) {
      e
    }

  private suspend fun cancellationOf(block: suspend () -> Unit): CancellationException =
    try {
      block()
      fail("expected a CancellationException")
    }
    catch (e: CancellationException) {
      e
    }

  private companion object {
    val TTL = 20.seconds
  }
}
