// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject

import com.intellij.mock.MockVirtualFile
import com.intellij.python.pyproject.model.internal.platformBridge.PendingRebuild
import com.intellij.python.pyproject.model.internal.platformBridge.PendingRebuildRequests
import com.intellij.python.pyproject.model.internal.platformBridge.RebuildRequest
import com.intellij.python.pyproject.model.internal.platformBridge.collectRebuilds
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

@Subsystems.IDE
@Layers.Functional
@Timeout(30)
internal class RebuildWithRetryTest {
  private val first = MockVirtualFile.dir("first")
  private val second = MockVirtualFile.dir("second")
  private val third = MockVirtualFile.dir("third")
  private val retryDelays = listOf(5.milliseconds, 10.milliseconds)

  @Test
  fun testRetriesBackOffAndSuccessClearsFailedWork(): Unit = timeoutRunBlocking {
    val reported = mutableListOf<Exception>()
    val completed = mutableListOf<PendingRebuild>()
    val attempts = mutableListOf<Pair<String, Duration>>()
    val started = TimeSource.Monotonic.markNow()
    flowOf(batch(first), batch(second)).collectRebuilds(retryDelays, { _, error -> reported.add(error) }) { batch ->
      attempts.add(batch.reason to started.elapsedNow())
      if (attempts.count { it.first == batch.reason } < 3) throw IOException(batch.reason)
      completed.add(batch)
    }

    assertThat(reported.map { it.message }).containsExactly("first", "second")
    assertThat(completed.map { it.directoriesToLoad }).containsExactly(setOf(first), setOf(second))
    assertThat(attempts.map { it.first }).containsExactly("first", "first", "first", "second", "second", "second")
    for (offset in listOf(0, 3)) {
      assertThat(attempts[offset + 1].second - attempts[offset].second).isGreaterThanOrEqualTo(retryDelays[0])
      assertThat(attempts[offset + 2].second - attempts[offset + 1].second).isGreaterThanOrEqualTo(retryDelays[1])
    }
  }

  @Test
  fun testExhaustedRetriesWaitForANewChangeAndKeepFailedDirectories(): Unit = timeoutRunBlocking {
    val requests = PendingRebuildRequests()
    val attempted = Channel<PendingRebuild>(Channel.UNLIMITED)
    val completed = Channel<PendingRebuild>(Channel.UNLIMITED)
    val reported = mutableListOf<Exception>()
    var fail = true
    val collector = launch {
      requests.batches(Duration.ZERO).collectRebuilds(retryDelays, { _, error -> reported.add(error) }) { batch ->
        attempted.send(batch)
        if (fail) throw IOException("Cannot load the subtree")
        completed.send(batch)
      }
    }
    try {
      requests.add(RebuildRequest(setOf(first), "first"))
      repeat(3) { assertThat(attempted.receive().directoriesToLoad).containsExactly(first) }
      assertThat(withTimeoutOrNull(100.milliseconds) { attempted.receive() }).isNull()
      assertThat(collector.isActive).isTrue()
      assertThat(reported).hasSize(1)

      fail = false
      requests.add(RebuildRequest(setOf(second), "second"))
      assertThat(completed.receive().directoriesToLoad).containsExactlyInAnyOrder(first, second)
      requests.add(RebuildRequest(setOf(third), "third"))
      assertThat(completed.receive().directoriesToLoad).containsExactly(third)
    }
    finally {
      collector.cancelAndJoin()
      attempted.cancel()
      completed.cancel()
    }
  }

  @ParameterizedTest
  @ValueSource(ints = [0, 1, 2])
  fun testMergingFailedWorkPreservesFullScans(scenario: Int): Unit = timeoutRunBlocking {
    val fullScan = PendingRebuild(emptySet(), "full scan", reloadProjectRoots = true)
    val hundredDirectories = PendingRebuild((1..100).map { MockVirtualFile.dir("dir$it") }.toSet(), "hundred", false)
    val (failed, next) = when (scenario) {
      0 -> fullScan to batch(second)
      1 -> batch(first) to fullScan
      2 -> hundredDirectories to batch(second)
      else -> error("Unknown scenario: $scenario")
    }
    var attempts = 0
    var result: PendingRebuild? = null
    val reported = mutableListOf<Exception>()
    flowOf(failed, next).collectRebuilds(retryDelays, { _, error -> reported.add(error) }) { batch ->
      if (++attempts <= 3) throw IOException("Cannot load the subtree")
      result = batch
    }

    assertThat(attempts).isEqualTo(4)
    assertThat(reported).hasSize(1)
    assertThat(checkNotNull(result).reloadProjectRoots).isTrue()
    assertThat(checkNotNull(result).directoriesToLoad).isEmpty()
  }

  @Test
  fun testPersistentFailureIsReportedOnceAcrossChanges(): Unit = timeoutRunBlocking {
    val attempted = mutableListOf<PendingRebuild>()
    val reported = mutableListOf<Exception>()
    flowOf(batch(first), batch(second), batch(third))
      .collectRebuilds(retryDelays, { _, error -> reported.add(error) }) { batch ->
        attempted.add(batch)
        throw IOException("Cannot rebuild")
      }

    assertThat(attempted).hasSize(9)
    assertThat(attempted.last().directoriesToLoad).containsExactlyInAnyOrder(first, second, third)
    assertThat(reported).hasSize(1)
  }

  @Test
  fun testChangesDuringARetryRemainPending(): Unit = timeoutRunBlocking {
    val requests = PendingRebuildRequests()
    val completed = Channel<PendingRebuild>(Channel.UNLIMITED)
    var attempts = 0
    requests.add(RebuildRequest(setOf(first), "first"))
    val collector = launch {
      requests.batches(Duration.ZERO).collectRebuilds(retryDelays, { _, _ ->
        requests.add(RebuildRequest(setOf(second), "during the retry"))
      }) { batch ->
        if (++attempts == 1) throw IOException("Cannot rebuild")
        completed.send(batch)
      }
    }
    try {
      assertThat(completed.receive().directoriesToLoad).containsExactly(first)
      assertThat(completed.receive().directoriesToLoad).containsExactly(second)
      assertThat(attempts).isEqualTo(3)
    }
    finally {
      collector.cancelAndJoin()
      completed.cancel()
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun testCancellationStopsTheBuildOrBackoff(duringBackoff: Boolean): Unit = timeoutRunBlocking {
    var attempts = 0
    val reported = mutableListOf<Exception>()
    val collector = launch(start = CoroutineStart.UNDISPATCHED) {
      flowOf(batch(first)).collectRebuilds(listOf(1.minutes), { _, error -> reported.add(error) }) {
        attempts++
        if (duringBackoff) throw IOException("Cannot rebuild")
        awaitCancellation()
      }
    }
    collector.cancelAndJoin()

    assertThat(attempts).isEqualTo(1)
    assertThat(reported).hasSize(if (duringBackoff) 1 else 0)
  }

  @Test
  fun testErrorsAreNotRetried() {
    val failure = AssertionError("Broken invariant")
    val reported = mutableListOf<Exception>()
    var attempts = 0
    val thrown = assertThrows<AssertionError> {
      timeoutRunBlocking {
        flowOf(batch(first)).collectRebuilds(retryDelays, { _, error -> reported.add(error) }) {
          attempts++
          throw failure
        }
      }
    }
    assertThat(thrown).isSameAs(failure)
    assertThat(attempts).isEqualTo(1)
    assertThat(reported).isEmpty()
  }

  @Test
  fun testProducerFailuresPropagate() {
    val reported = mutableListOf<Exception>()
    var builds = 0
    assertThrows<IOException> {
      timeoutRunBlocking {
        flow {
          emit(batch(first))
          throw IOException("The producer failed")
        }.collectRebuilds(retryDelays, { _, error -> reported.add(error) }) { builds++ }
      }
    }
    assertThat(builds).isEqualTo(1)
    assertThat(reported).isEmpty()
  }

  private fun batch(directory: MockVirtualFile): PendingRebuild = PendingRebuild(setOf(directory), directory.name, false)
}
