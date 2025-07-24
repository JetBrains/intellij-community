package org.jetbrains.plugins.textmate.cache

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.*
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.jetbrains.plugins.textmate.TestUtilMultiplatform
import org.jetbrains.plugins.textmate.update
import org.junit.jupiter.api.Test
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.random.Random
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

@OptIn(ExperimentalAtomicApi::class)
class SLRUTextMateCacheTest {
  companion object {
    private val TIMEOUT = 10.seconds
  }

  @Test
  fun `should create cache with valid parameters`() {
    val cache = SLRUTextMateCache<String, String>(
      capacity = 10,
      computeFn = { "value-$it" },
      disposeFn = { }
    )

    assertEquals(0, cache.size())
    assertFalse(cache.contains("nonexistent"))
  }

  @Test
  fun `should reject invalid constructor parameters`() {
    assertFailsWith<IllegalArgumentException> {
      SLRUTextMateCache<String, String>(
        capacity = 0,
        computeFn = { "value-$it" },
        disposeFn = { }
      )
    }

    assertFailsWith<IllegalArgumentException> {
      SLRUTextMateCache<String, String>(
        capacity = 10,
        computeFn = { "value-$it" },
        disposeFn = { },
        protectedRatio = 1.5
      )
    }

    assertFailsWith<IllegalArgumentException> {
      SLRUTextMateCache<String, String>(
        capacity = 10,
        computeFn = { "value-$it" },
        disposeFn = { },
        protectedRatio = -0.1
      )
    }
  }

  @Test
  fun `should compute values for new keys`() {
    val computeCallCount = AtomicInt(0)
    val cache = SLRUTextMateCache<String, String>(
      capacity = 10,
      computeFn = { key ->
        computeCallCount.incrementAndFetch()
        "computed-$key"
      },
      disposeFn = { }
    )

    val result = cache.use("test-key") { value ->
      assertEquals("computed-test-key", value)
      "result"
    }

    assertEquals("result", result)
    assertEquals(1, computeCallCount.load())
    assertEquals(1, cache.size())
    assertTrue(cache.contains("test-key"))
  }

  @Test
  fun `should reuse cached values`() {
    val computeCallCount = AtomicInt(0)
    val cache = SLRUTextMateCache<String, String>(
      capacity = 10,
      computeFn = { key ->
        computeCallCount.incrementAndFetch()
        "computed-$key"
      },
      disposeFn = { }
    )

    cache.use("test-key") { value ->
      assertEquals("computed-test-key", value)
    }

    cache.use("test-key") { value ->
      assertEquals("computed-test-key", value)
    }

    assertEquals(1, computeCallCount.load(), "Value should be computed only once")
    assertEquals(1, cache.size())
  }

  @Test
  fun `should promote entries from probationary to protected`() {
    val cache = SLRUTextMateCache<String, String>(
      capacity = 4,
      protectedRatio = 0.5, // 2 protected, 2 probationary
      computeFn = { "value-$it" },
      disposeFn = { }
    )

    // Fill probationary segment
    cache.use("prob1") { it }
    cache.use("prob2") { it }
    assertEquals(2, cache.size())

    // Access again - should promote to protected
    cache.use("prob1") { it }

    // Fill probationary segment again
    cache.use("prob3") { it }
    cache.use("prob4") { it }
    assertEquals(4, cache.size())

    // Add one more - should evict from probationary, not protected
    cache.use("prob5") { it }

    assertEquals(4, cache.size())
    assertTrue(cache.contains("prob1"), "Promoted entry should survive eviction")
    assertFalse(cache.contains("prob2"), "Non-promoted entry should be evicted")
  }

  @Test
  fun `should handle different protection ratios`() {
    val testRatios = listOf(0.2, 0.5, 0.8)

    testRatios.forEach { ratio ->
      val cache = SLRUTextMateCache<String, String>(
        capacity = 10,
        protectedRatio = ratio,
        computeFn = { "value-$it" },
        disposeFn = { }
      )

      repeat(15) { i ->
        cache.use("key$i") { }
      }

      assertEquals(10, cache.size(), "Cache should maintain capacity with ratio $ratio")
    }
  }

  @Test
  fun `should evict LRU entries within each segment`() {
    val cache = SLRUTextMateCache<String, String>(
      capacity = 4,
      protectedRatio = 0.5,
      computeFn = { "value-$it" },
      disposeFn = { }
    )

    // Fill probationary
    cache.use("prob1") { it }
    cache.use("prob2") { it }

    // Promote prob2 to protected
    cache.use("prob2") { it }
    // Promote prob1 to protected, make it more recently used than prob2
    cache.use("prob1") { it }

    // Fill probationary again
    cache.use("prob3") { it }
    cache.use("prob4") { it }

    // Promote prob3 to protected, prob2 should be evicted
    cache.use("prob3") { it }

    cache.use("prob5") { it }
    // prob4 should be evicted
    cache.use("prob6") { it }

    assertTrue(cache.contains("prob1"), "Protected entry should survive")
    assertTrue(cache.contains("prob3"), "Recently used probationary entry should survive")
    assertFalse(cache.contains("prob4"), "LRU probationary entry should be evicted")
    assertTrue(cache.contains("prob5"), "New entry should be present")
    assertTrue(cache.contains("prob6"), "New entry should be present")
  }

  @Test
  fun `should handle concurrent access to same key`() = runConcurrentTest {
    val cache = SLRUTextMateCache<String, String>(
      capacity = 10,
      computeFn = { key ->
        TestUtilMultiplatform.threadSleep(10) // Simulate blocking computation time
        "computed-$key-${Random.nextInt()}"
      },
      disposeFn = { }
    )

    val startSignal = Job()
    val jobs = List(size = 10) {
      async(Dispatchers.Default) {
        startSignal.join()
        cache.use("shared-key") { it }
      }
    }
    startSignal.complete()
    val results = jobs.awaitAll()
    assertEquals(10, results.size)
    assertEquals(1, results.toSet().size, "results should be unique: $results")
  }

  @Test
  fun `should handle concurrent access to different keys`() = runConcurrentTest {
    val cache = SLRUTextMateCache<String, String>(
      capacity = 100,
      computeFn = { key -> "value-$key" },
      disposeFn = { }
    )

    val results = AtomicReference<PersistentList<String>>(persistentListOf())
    val exceptions = AtomicReference<PersistentList<Throwable>>(persistentListOf())

    val jobs = List(100) { taskId ->
      launch(Dispatchers.Default) {
        runCatching {
          val result = cache.use("key-$taskId") { value ->
            assertEquals("value-key-$taskId", value)
            "result-$taskId"
          }
          results.update { it.add(result) }
        }.onFailure { e ->
          exceptions.update { it.add(e) }
        }
      }
    }

    jobs.joinAll()

    assertTrue(exceptions.load().isEmpty(), "No exceptions should occur: ${exceptions.load()}")
    assertEquals(100, results.load().size)
    assertEquals(100, cache.size())
  }

  @Test
  fun `should handle concurrent eviction and access`() = runConcurrentTest {
    val cache = SLRUTextMateCache<String, String>(
      capacity = 5,
      computeFn = { key -> "value-$key" },
      disposeFn = { }
    )

    val exceptions = AtomicReference<PersistentList<Throwable>>(persistentListOf())

    // Background coroutine continuously adding entries (causing evictions)
    val evictionJob = launch(Dispatchers.Default) {
      var counter = 0
      repeat(1000) {
        runCatching {
          cache.use("evict-key-${counter++}") { it }
          delay(1)
        }.onFailure { e ->
          exceptions.update { it.add(e) }
        }
      }
    }

    // Multiple coroutines accessing potentially evicted entries
    val accessJobs = List(5) { coroutineId ->
      launch(Dispatchers.Default) {
        repeat(50) { iteration ->
          runCatching {
            cache.use("access-key-$coroutineId-$iteration") { value ->
              delay(10) // Simulate work
              value
            }
          }.onFailure { e ->
            exceptions.update { it.add(e) }
          }
        }
      }
    }

    listOf(evictionJob, *accessJobs.toTypedArray()).joinAll()

    assertTrue(exceptions.load().isEmpty(), "No exceptions should occur: ${exceptions.load()}")
    assertTrue(cache.size() <= 5, "Cache should not exceed capacity")
  }

  @Test
  fun `should handle concurrent cleanup and access`() = runConcurrentTest {
    val cache = SLRUTextMateCache<String, String>(
      capacity = 10,
      computeFn = { key -> "value-$key" },
      disposeFn = { }
    )

    val exceptions = AtomicReference<PersistentList<Throwable>>(persistentListOf())

    // Coroutine continuously calling cleanup
    val cleanupJob = launch(Dispatchers.Default) {
      repeat(100) {
        runCatching {
          cache.cleanup()
          delay(10)
        }.onFailure { e ->
          exceptions.update { it.add(e) }
        }
      }
    }

    // Coroutines accessing cache during cleanup
    val accessJobs = List(3) { coroutineId ->
      launch(Dispatchers.Default) {
        repeat(100) { iteration ->
          runCatching {
            cache.use("key-$coroutineId-$iteration") { it }
            delay(1)
          }.onFailure { e ->
            exceptions.update { it.add(e) }
          }
        }
      }
    }

    listOf(cleanupJob, *accessJobs.toTypedArray()).joinAll()

    assertTrue(exceptions.load().isEmpty(), "No exceptions should occur: ${exceptions.load()}")
  }

  @Test
  fun `stress test`() = runConcurrentTest {
    val cache = SLRUTextMateCache<String, String>(
      capacity = 20,
      computeFn = { key -> "value-$key" },
      disposeFn = { }
    )

    val operations = AtomicInt(0)
    val exceptions = AtomicReference<PersistentList<Throwable>>(persistentListOf())

    // Launch many coroutines doing random operations
    val jobs = List(20) { coroutineId ->
      launch(Dispatchers.Default) {
        repeat(500) { iteration ->
          runCatching {
            when (iteration % 10) {
              in 0..6 -> {
                // 70% cache access
                cache.use("key-${(coroutineId * 100 + iteration) % 50}") { it }
              }
              in 7..8 -> {
                // 20% cleanup
                cache.cleanup()
              }
              else -> {
                // 10% size/contains check
                cache.size()
                cache.contains("key-${iteration % 50}")
              }
            }
            operations.incrementAndFetch()

            // Small random delay
            delay((0..5).random().toLong())
          }.onFailure { e ->
            exceptions.update { it.add(e) }
          }
        }
      }
    }

    jobs.joinAll()

    println("Coroutines stress test completed ${operations.load()} operations")
    assertTrue(operations.load() > 5000, "Should complete significant number of operations")
    assertTrue(exceptions.load().isEmpty(), "No exceptions should occur: ${exceptions.load()}")
    assertTrue(cache.size() <= 20, "Cache should maintain capacity")
  }

  @Test
  fun `should handle mixed sync and async operations`() = runConcurrentTest {
    val cache = SLRUTextMateCache<String, String>(
      capacity = 10,
      computeFn = { key ->
        // Simulate mixed computation (sometimes fast, sometimes slow)
        if (key.contains("slow")) {
          TestUtilMultiplatform.threadSleep(50) // Blocking operation
        }
        else {
          TestUtilMultiplatform.threadSleep(1)  // Fast operation
        }
        "computed-$key"
      },
      disposeFn = { }
    )

    val results = AtomicReference<PersistentList<String>>(persistentListOf())

    // Mix of fast and slow operations
    val jobs = mutableListOf<Job>()

    // Fast operations
    repeat(5) { i ->
      jobs.add(launch(Dispatchers.Default) {
        val result = cache.use("fast-key-$i") { value ->
          delay(1)
          "fast-result-$value"
        }
        results.update { it.add(result) }
      })
    }

    // Slow operations
    repeat(3) { i ->
      jobs.add(launch(Dispatchers.Default) {
        val result = cache.use("slow-key-$i") { value ->
          delay(10) // Longer async work
          "slow-result-$value"
        }
        results.update { it.add(result) }
      })
    }

    // Wait for all
    jobs.joinAll()

    assertEquals(8, results.load().size)
    assertTrue(results.load().any { it.contains("fast-result") })
    assertTrue(results.load().any { it.contains("slow-result") })
  }

  @Test
  fun `should dispose evicted entries when not in use`() {
    val disposedValues = AtomicReference<PersistentSet<String>>(persistentSetOf())
    val cache = SLRUTextMateCache<String, String>(
      capacity = 2,
      computeFn = { key -> "value-$key" },
      disposeFn = { value -> disposedValues.update { it.add(value) } }
    )

    cache.use("key1") { it }
    cache.use("key2") { it }

    // should evict key1
    cache.use("key3") { it }

    assertTrue(disposedValues.load().contains("value-key1"), "Evicted entry should be disposed")
    assertFalse(cache.contains("key1"), "Evicted entry should not be in cache")
  }

  @Test
  fun `should defer disposal of entries in use`() = runConcurrentTest {
    val disposedValues = AtomicReference<PersistentSet<String>>(persistentSetOf())
    val cache = SLRUTextMateCache<String, String>(
      capacity = 2,
      computeFn = { key -> "value-$key" },
      disposeFn = { value -> disposedValues.update { it.add(value) } }
    )

    val key1IsInUse = Job()
    try {
      val longRunningTask = async(Dispatchers.Default) {
        cache.use("key1") {
          key1IsInUse.complete()
          awaitCancellation()
        }
      }
      key1IsInUse.join()

      // Fill the cache and evict key1 while it's in use
      cache.use("key2") { it }
      cache.use("key3") { it } // This should evict key1, but not dispose it yet

      assertFalse(disposedValues.load().contains("value-key1"), "Entry in use should not be disposed")

      longRunningTask.cancelAndJoin()
      assertTrue(disposedValues.load().contains("value-key1"), "Entry should be disposed after use completes")
    }
    finally {
      key1IsInUse.complete()
    }
  }

  @Test
  fun `should handle multiple concurrent users of same entry`() = runConcurrentTest {
    val disposedValues = AtomicReference<PersistentSet<String>>(persistentSetOf())
    val cache = SLRUTextMateCache<String, String>(
      capacity = 2,
      computeFn = { key -> "value-$key" },
      disposeFn = { value -> disposedValues.update { it.add(value) } }
    )

    suspend fun useKeyAndWaitUntilReallyUsed(): Job {
      val keyIsInUse = Job()
      val result = launch(Dispatchers.Default) {
        cache.use("shared-key") {
          keyIsInUse.complete()
          awaitCancellation()
        }
      }
      keyIsInUse.join()
      return result
    }

    val usingJobs = listOf(useKeyAndWaitUntilReallyUsed(),
                           useKeyAndWaitUntilReallyUsed(),
                           useKeyAndWaitUntilReallyUsed())

    // Force eviction from protected while entry is in use by multiple threads
    cache.use("key2") { it }
    cache.use("key2") { it }

    assertFalse(disposedValues.load().contains("value-shared-key"), "Entry in use by multiple threads should not be disposed")

    usingJobs.forEach { it.cancelAndJoin() }
    assertTrue(disposedValues.load().contains("value-shared-key"), "Entry should be disposed after all uses complete")
  }

  @Test
  fun `cleanup should remove unused entries`() {
    val disposedValues = AtomicReference<PersistentSet<String>>(persistentSetOf())
    val cache = SLRUTextMateCache<String, String>(
      capacity = 10,
      computeFn = { key -> "value-$key" },
      disposeFn = { value -> disposedValues.update { it.add(value) } }
    )

    // Add entries
    repeat(5) { i ->
      cache.use("key$i") { }
    }

    assertEquals(5, cache.size())

    cache.cleanup()

    assertEquals(0, cache.size())
    assertEquals(5, disposedValues.load().size, "No entries should be disposed from cleanup")
  }

  @Test
  fun `clear should dispose unused entries immediately`() {
    val disposedValues = AtomicReference<PersistentSet<String>>(persistentSetOf())
    val cache = SLRUTextMateCache<String, String>(
      capacity = 10,
      computeFn = { key -> "value-$key" },
      disposeFn = { value -> disposedValues.update { it.add(value) } }
    )

    repeat(5) { i ->
      cache.use("key$i") { }
    }

    assertEquals(5, cache.size())

    cache.clear()

    assertEquals(0, cache.size())
    assertEquals(List(5) { "value-key$it" }.toSet(), disposedValues.load().toSet(), "All entries should be disposed")
  }

  @Test
  fun `clear should defer disposal of entries in use`() = runConcurrentTest {
    val disposedValues = AtomicReference<PersistentSet<String>>(persistentSetOf())
    val cache = SLRUTextMateCache<String, String>(
      capacity = 10,
      computeFn = { key -> "value-$key" },
      disposeFn = { value -> disposedValues.update { it.add(value) } }
    )

    cache.use("key1") { it }
    cache.use("key2") { it }

    val keyInUseJob = Job()
    val useJob = launch(Dispatchers.Default) {
      cache.use("key1") {
        keyInUseJob.complete()
        awaitCancellation()
      }
    }

    keyInUseJob.join()

    // Clear cache while key1 is in use
    cache.clear()

    assertEquals(0, cache.size(), "Cache should be empty after clear")
    assertTrue(disposedValues.load().contains("value-key2"), "Unused entry should be disposed immediately")
    assertFalse(disposedValues.load().contains("value-key1"), "Entry in use should not be disposed yet")

    useJob.cancelAndJoin()
    assertTrue(disposedValues.load().contains("value-key1"), "Entry should be disposed after use completes")
  }

  @Test
  fun `should handle exceptions in computeFn`() {
    val cache = SLRUTextMateCache<String, String>(
      capacity = 10,
      computeFn = { key ->
        if (key == "bad-key") {
          error("Computation failed")
        }
        "value-$key"
      },
      disposeFn = { }
    )

    // Exception in computeFn should propagate
    assertFailsWith<RuntimeException> {
      cache.use("bad-key") { }
    }

    // Cache should remain functional
    val result = cache.use("good-key") { it }
    assertEquals("value-good-key", result)
    assertEquals(1, cache.size())
  }

  @Test
  fun `should handle exceptions in disposeFn`() {
    val disposeAttempts = AtomicReference<PersistentSet<String>>(persistentSetOf())
    val cache = SLRUTextMateCache<String, String>(
      capacity = 2,
      computeFn = { key -> "value-$key" },
      disposeFn = { value ->
        disposeAttempts.update { it.add(value) }
        if (value == "value-key1") {
          error("Dispose failed")
        }
      }
    )

    // Fill cache
    cache.use("key1") { it }
    cache.use("key2") { it }

    // Add third entry - should try to dispose key1, but exception should not break cache
    cache.use("key3") { it }

    // Cache should remain functional
    assertEquals(2, cache.size())
    assertTrue(cache.contains("key2"))
    assertTrue(cache.contains("key3"))

    // Verify dispose was attempted
    assertTrue(disposeAttempts.load().contains("value-key1"))
  }

  @Test
  fun `should handle exceptions in user code`() {
    val cache = SLRUTextMateCache<String, String>(
      capacity = 10,
      computeFn = { key -> "value-$key" },
      disposeFn = { }
    )

    // Exception in user code should propagate but not break cache
    assertFailsWith<RuntimeException> {
      cache.use("test-key") {
        error("User code failed")
      }
    }

    // Cache should remain functional and entry should be available
    val result = cache.use("test-key") { it }
    assertEquals("value-test-key", result)
    assertEquals(1, cache.size())
  }

  @Test
  fun `should handle concurrent exceptions`() = runConcurrentTest {
    val cache = SLRUTextMateCache<String, String>(
      capacity = 10,
      computeFn = { key -> "value-$key" },
      disposeFn = { value ->
        if (value.contains("bad")) {
          error("Dispose failed")
        }
      }
    )

    val exceptions = AtomicReference<PersistentList<Throwable>>(persistentListOf())
    val jobs = List(20) { i ->
      launch {
        runCatching {
          val key = if (i % 3 == 0) "bad-key-$i" else "good-key-$i"
          cache.use(key) { value ->
            if (i % 5 == 0) {
              error("User exception")
            }
            value
          }
        }.onFailure { e ->
          exceptions.update { it.add(e) }
        }
      }
    }
    jobs.joinAll()

    assertTrue(exceptions.load().isNotEmpty())
    cache.use("final-test") { it }
    assertTrue(cache.size() > 0, "Cache should still be functional")
  }

  @Test
  fun `should handle capacity of 1`() {
    val cache = SLRUTextMateCache<String, String>(
      capacity = 1,
      computeFn = { key -> "value-$key" },
      disposeFn = { }
    )

    cache.use("key1") { it }
    assertEquals(1, cache.size())

    cache.use("key2") { it }
    assertEquals(1, cache.size())

    assertFalse(cache.contains("key1"))
    assertTrue(cache.contains("key2"))
  }

  @Test
  fun `should handle extreme protection ratios`() {
    val allProtected = SLRUTextMateCache<String, String>(
      capacity = 10,
      protectedRatio = 1.0,
      computeFn = { key -> "value-$key" },
      disposeFn = { }
    )

    repeat(15) { i ->
      allProtected.use("key$i") { }
    }
    assertEquals(10, allProtected.size())

    val allProbationary = SLRUTextMateCache<String, String>(
      capacity = 10,
      protectedRatio = 0.0,
      computeFn = { key -> "value-$key" },
      disposeFn = { }
    )

    repeat(15) { i ->
      allProbationary.use("key$i") { }
    }
    assertEquals(10, allProbationary.size())
  }

  @Test
  fun `should handle null values from computeFn`() {
    val cache = SLRUTextMateCache<String, String?>(
      capacity = 10,
      computeFn = { key ->
        if (key == "null-key") null else "value-$key"
      },
      disposeFn = { }
    )

    val result = cache.use("null-key") { value ->
      assertNull(value)
      "handled-null"
    }
    assertEquals("handled-null", result)
  }

  @Test
  fun `should handle rapid adds and removes`() {
    val cache = SLRUTextMateCache<String, String>(
      capacity = 5,
      computeFn = { key -> "value-$key" },
      disposeFn = { }
    )

    // Rapid cycling through many keys
    repeat(100) { i ->
      cache.use("key-${i % 20}") { }
    }

    assertTrue(cache.size() <= 5)

    // Cache should still be functional
    cache.use("final-key") { }
    assertTrue(cache.contains("final-key"))
  }

  @Test
  fun `should maintain performance under load`() {
    val cache = SLRUTextMateCache<String, String>(
      capacity = 100,
      computeFn = { key -> "value-$key" },
      disposeFn = { }
    )

    val duration = measureTime {
      repeat(10000) { i ->
        cache.use("key-${i % 200}") { } // 50% hit rate
      }
    }

    println("10,000 operations took $duration")
    assertTrue(duration < 5.seconds, "Operations should complete within reasonable time")
    assertEquals(100, cache.size(), "Cache should maintain capacity")
  }

  @Test
  fun `should handle concurrent load efficiently`() = runConcurrentTest {
    val cache = SLRUTextMateCache<String, String>(
      capacity = 50,
      computeFn = { key ->
        TestUtilMultiplatform.threadSleep(1) // Simulate computation
        "value-$key"
      },
      disposeFn = { }
    )

    val completedTasks = AtomicInt(0)

    val duration = measureTime {
      val jobs = List(1000) { i ->
        launch {
          cache.use("key-${i % 100}") { it } // 50% hit rate
          completedTasks.incrementAndFetch()
        }
      }
      jobs.joinAll()
    }

    println("1,000 concurrent operations took $duration")

    assertEquals(1000, completedTasks.load())
    assertTrue(duration < 15.seconds, "Concurrent operations should complete efficiently")
  }

  @Test
  fun `should scale with different capacity sizes`() {
    val capacities = listOf(10, 100, 1000)

    capacities.forEach { capacity ->
      val cache = SLRUTextMateCache<String, String>(
        capacity = capacity,
        computeFn = { key -> "value-$key" },
        disposeFn = { }
      )

      // Operations proportional to capacity
      val duration = measureTime {
        repeat(capacity * 10) { i ->
          cache.use("key-${i % (capacity * 2)}") { }
        }
      }
      println("Capacity $capacity: ${capacity * 10} operations took ${duration}")

      assertEquals(capacity, cache.size())
      assertTrue(duration < 10.seconds, "Should scale reasonably with capacity")
    }
  }

  @Test
  fun `should handle memory pressure gracefully`() {
    val disposedCount = AtomicInt(0)
    val cache = SLRUTextMateCache<String, ByteArray>(
      capacity = 100,
      computeFn = { key ->
        ByteArray(1024) { key.hashCode().toByte() } // 1KB per entry
      },
      disposeFn = { _ -> disposedCount.incrementAndFetch() }
    )

    repeat(200) { i ->
      cache.use("large-object-$i") { data ->
        data.size
      }
    }

    assertEquals(100, cache.size(), "Cache should maintain capacity")
    assertTrue(disposedCount.load() >= 100, "Should dispose evicted large objects")

    // Verify cache is still functional
    val result = cache.use("test-key") { it.size }
    assertEquals(1024, result)
  }

  private fun runConcurrentTest(testBody: suspend TestScope.() -> Unit) = runTest(timeout = TIMEOUT, testBody = testBody)
}