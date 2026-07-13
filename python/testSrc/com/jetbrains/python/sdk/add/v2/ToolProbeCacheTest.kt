// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.idea.TestFor
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

@TestFor(classes = [ToolProbeCache::class])
class ToolProbeCacheTest {

  @Test
  fun `successful values and misses are cached`() = runBlocking {
    val cache = ToolProbeCache<String, String>()
    var loadCount = 0

    val first = cache.getOrLoad(listOf("uv", "poetry")) { keys ->
      loadCount++
      assertEquals(listOf("uv", "poetry"), keys)
      PyResult.success(mapOf("uv" to "/bin/uv"))
    }.orThrow()
    val second = cache.getOrLoad(listOf("uv", "poetry")) {
      fail("Cached values and misses must not be loaded again")
    }.orThrow()

    assertEquals(mapOf("uv" to "/bin/uv"), first)
    assertEquals(first, second)
    assertEquals(1, loadCount)
  }

  @Test
  fun `only uncached keys are loaded`() = runBlocking {
    val cache = ToolProbeCache<String, String>()
    cache.getOrLoad(listOf("uv")) {
      PyResult.success(mapOf("uv" to "/bin/uv"))
    }.orThrow()

    val result = cache.getOrLoad(listOf("uv", "poetry")) { keys ->
      assertEquals(listOf("poetry"), keys)
      PyResult.success(mapOf("poetry" to "/bin/poetry"))
    }.orThrow()

    assertEquals(mapOf("uv" to "/bin/uv", "poetry" to "/bin/poetry"), result)
  }

  @Test
  fun `failure is not cached`() = runBlocking {
    val cache = ToolProbeCache<String, String>()
    var loadCount = 0

    val failure = cache.getOrLoad(listOf("uv")) {
      loadCount++
      PyResult.localizedError("probe failed")
    }
    val success = cache.getOrLoad(listOf("uv")) {
      loadCount++
      PyResult.success(mapOf("uv" to "/bin/uv"))
    }.orThrow()

    assertTrue(failure is Result.Failure)
    assertEquals(mapOf("uv" to "/bin/uv"), success)
    assertEquals(2, loadCount)
  }

  @Test
  fun `exception does not block a later load`() = runBlocking {
    val cache = ToolProbeCache<String, String>()

    val failure = runCatching {
      cache.getOrLoad(listOf("uv")) {
        error("probe failed")
      }
    }
    val success = cache.getOrLoad(listOf("uv")) {
      PyResult.success(mapOf("uv" to "/bin/uv"))
    }.orThrow()

    assertTrue(failure.exceptionOrNull() is IllegalStateException)
    assertEquals(mapOf("uv" to "/bin/uv"), success)
  }

  @Test
  fun `cancelled load does not cancel another request`() = runBlocking {
    val cache = ToolProbeCache<String, String>()
    val loaderStarted = CompletableDeferred<Unit>()

    val cancelled = async(start = CoroutineStart.UNDISPATCHED) {
      cache.getOrLoad(listOf("uv")) {
        loaderStarted.complete(Unit)
        awaitCancellation()
      }
    }
    loaderStarted.await()
    val waiting = async(start = CoroutineStart.UNDISPATCHED) {
      cache.getOrLoad(listOf("uv")) {
        PyResult.success(mapOf("uv" to "/bin/uv"))
      }
    }

    cancelled.cancelAndJoin()

    assertEquals(mapOf("uv" to "/bin/uv"), waiting.await().orThrow())
  }

  @Test
  fun `concurrent requests share a successful load`() = runBlocking {
    val cache = ToolProbeCache<String, String>()
    val loaderStarted = CompletableDeferred<Unit>()
    val releaseLoader = CompletableDeferred<Unit>()
    var loadCount = 0

    val first = async(start = CoroutineStart.UNDISPATCHED) {
      cache.getOrLoad(listOf("uv")) {
        loadCount++
        loaderStarted.complete(Unit)
        releaseLoader.await()
        PyResult.success(mapOf("uv" to "/bin/uv"))
      }
    }
    loaderStarted.await()
    val second = async(start = CoroutineStart.UNDISPATCHED) {
      cache.getOrLoad(listOf("uv")) {
        loadCount++
        PyResult.success(mapOf("uv" to "/other/uv"))
      }
    }

    releaseLoader.complete(Unit)

    assertEquals(mapOf("uv" to "/bin/uv"), first.await().orThrow())
    assertEquals(mapOf("uv" to "/bin/uv"), second.await().orThrow())
    assertEquals(1, loadCount)
  }
}
