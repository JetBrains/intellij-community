// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject

import com.intellij.idea.TestFor
import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.pyproject.model.internal.platformBridge.PendingRebuild
import com.intellij.python.pyproject.model.internal.platformBridge.PendingRebuildRequests
import com.intellij.python.pyproject.model.internal.platformBridge.RebuildRequest
import com.intellij.python.pyproject.model.internal.platformBridge.mergeRebuildRequests
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

@Subsystems.IDE
@Layers.Functional
@TestFor(issues = ["PY-91841"])
@Timeout(30)
internal class PendingRebuildRequestsTest {
  private val first = MockVirtualFile.dir("first")
  private val second = MockVirtualFile.dir("second")
  private val third = MockVirtualFile.dir("third")

  @Test
  fun testCompletedSourcesFlushTheirMergedBatch(): Unit = timeoutRunBlocking {
    val batches = flowOf(PendingRebuild.Directories(setOf(first), "first"))
      .mergeRebuildRequests(flowOf(PendingRebuild.Directories(setOf(second), "second")), 1.days)

    repeat(2) {
      val collected = batches.toList()
      assertThat(collected).hasSize(2)
      assertInstanceOf(PendingRebuild.FullScan::class.java, collected.first())
      val merged = assertInstanceOf(PendingRebuild.Directories::class.java, collected.last())
      assertThat(merged.directoriesToLoad).containsExactlyInAnyOrder(first, second)
    }
  }

  @Test
  fun testEmptySourcesCompleteAfterTheInitialScan(): Unit = timeoutRunBlocking {
    val collected = emptyFlow<PendingRebuild>().mergeRebuildRequests(emptyFlow(), Duration.ZERO).toList()
    assertThat(collected).hasSize(1)
    assertInstanceOf(PendingRebuild.FullScan::class.java, collected.single())
  }

  @ParameterizedTest
  @ValueSource(ints = [2, 101])
  fun testBothSourcesAccumulateDuringTheInitialScan(directoryCount: Int): Unit = timeoutRunBlocking {
    val directories = (1..directoryCount).map { MockVirtualFile.dir("dir$it") }
    val activeSources = AtomicInteger()
    val initialScanStarted = CompletableDeferred<Unit>()
    val finishInitialScan = CompletableDeferred<Unit>()
    val sent = List(2) { CompletableDeferred<Unit>() }
    val sources = sent.mapIndexed { index, completed ->
      flow {
        activeSources.incrementAndGet()
        try {
          initialScanStarted.await()
          directories.filterIndexed { i, _ -> i % 2 == index }.forEach {
            emit(PendingRebuild.Directories(setOf(it), it.name))
          }
          completed.complete(Unit)
          awaitCancellation()
        }
        finally {
          activeSources.decrementAndGet()
        }
      }
    }
    val collected = async {
      sources[0].mergeRebuildRequests(sources[1], Duration.ZERO)
        .onEach {
          if (!initialScanStarted.isCompleted) {
            assertThat(activeSources.get()).isEqualTo(2)
            assertInstanceOf(PendingRebuild.FullScan::class.java, it)
            initialScanStarted.complete(Unit)
            finishInitialScan.await()
          }
        }
        .take(2).toList()
    }
    initialScanStarted.await()
    sent.forEach { it.await() }
    finishInitialScan.complete(Unit)
    val batches = collected.await()
    assertThat(batches).hasSize(2)
    val pending = batches.last()
    if (directoryCount > 100) {
      assertInstanceOf(PendingRebuild.FullScan::class.java, pending)
    }
    else {
      val batch = assertInstanceOf(PendingRebuild.Directories::class.java, pending)
      assertThat(batch.directoriesToLoad).containsExactlyInAnyOrderElementsOf(directories)
    }
    assertThat(activeSources.get()).isZero()
  }

  @Test
  fun testSourceFailureCancelsTheOtherSource() {
    val failure = IOException("Cannot read workspace changes")
    var otherStopped = false
    val thrown = assertThrows<IOException> {
      timeoutRunBlocking {
        val otherStarted = CompletableDeferred<Unit>()
        val failing = flow<PendingRebuild> {
          otherStarted.await()
          throw failure
        }
        val other = flow<PendingRebuild> {
          try {
            otherStarted.complete(Unit)
            awaitCancellation()
          }
          finally {
            otherStopped = true
          }
        }
        failing.mergeRebuildRequests(other, Duration.ZERO).toList()
      }
    }
    assertThat(thrown).isSameAs(failure)
    assertThat(otherStopped).isTrue()
  }

  @Test
  fun testRequestsBeforeCollectionMergeWithoutCountingDuplicates(): Unit = timeoutRunBlocking {
    val requests = PendingRebuildRequests(directoryLimit = 2)
    requests.add(RebuildRequest(setOf(first), "first request"))
    requests.add(RebuildRequest(setOf(first, second), "latest request"))

    val batch = requests.batches(Duration.ZERO).first()
    assertThat(assertInstanceOf(PendingRebuild.Directories::class.java, batch).directoriesToLoad).containsExactlyInAnyOrder(first, second)
    assertThat(batch.reason).isEqualTo("latest request")
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun testOverflowRequestsAFullScan(singleRequest: Boolean): Unit = timeoutRunBlocking {
    val requests = PendingRebuildRequests(directoryLimit = 2)
    if (singleRequest) {
      requests.add(RebuildRequest(setOf(first, second, third), "large request"))
    }
    else {
      requests.add(RebuildRequest(setOf(first, second), "at the limit"))
      requests.add(RebuildRequest(setOf(third), "above the limit"))
    }
    requests.add(RebuildRequest(setOf(first), "another change"))

    val batch = requests.batches(Duration.ZERO).first()
    assertInstanceOf(PendingRebuild.FullScan::class.java, batch)

    requests.add(RebuildRequest(setOf(second), "a change during the full scan"))
    val next = requests.batches(Duration.ZERO).first()
    assertThat(assertInstanceOf(PendingRebuild.Directories::class.java, next).directoriesToLoad).containsExactly(second)
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun testChangesDuringABuildRemainPending(overflow: Boolean): Unit = timeoutRunBlocking {
    val requests = PendingRebuildRequests(directoryLimit = 2)
    val buildStarted = CompletableDeferred<PendingRebuild>()
    val finishBuild = CompletableDeferred<Unit>()
    requests.add(RebuildRequest(setOf(first), "before the build"))
    val collected = async {
      requests.batches(10.milliseconds)
        .onEach { batch ->
          if (buildStarted.complete(batch)) finishBuild.await()
        }
        .take(2)
        .toList()
    }
    val processing = buildStarted.await()

    requests.add(RebuildRequest(setOf(first), "first change during the build"))
    requests.add(RebuildRequest(setOf(second), "latest change during the build"))
    if (overflow) requests.add(RebuildRequest(setOf(third), "overflow during the build"))
    finishBuild.complete(Unit)

    val batches = collected.await()
    assertThat(batches).hasSize(2)
    assertThat(batches.first()).isSameAs(processing)
    assertThat(assertInstanceOf(PendingRebuild.Directories::class.java, processing).directoriesToLoad).containsExactly(first)
    val next = batches.last()
    if (overflow) {
      assertInstanceOf(PendingRebuild.FullScan::class.java, next)
    }
    else {
      assertThat(assertInstanceOf(PendingRebuild.Directories::class.java, next).directoriesToLoad).containsExactlyInAnyOrder(first, second)
      assertThat(next.reason).isEqualTo("latest change during the build")
    }
  }

  @Test
  fun testContentChangesRequestAnotherBuildWithoutDirectories(): Unit = timeoutRunBlocking {
    val requests = PendingRebuildRequests()
    repeat(2) {
      requests.add(RebuildRequest(emptySet(), "content changed"))
      val batch = requests.batches(Duration.ZERO).first()
      assertThat(assertInstanceOf(PendingRebuild.Directories::class.java, batch).directoriesToLoad).isEmpty()
      assertThat(batch.reason).isEqualTo("content changed")
    }
  }

  @Test
  fun testConcurrentProducersKeepAllDirectories(): Unit = timeoutRunBlocking {
    val requests = PendingRebuildRequests(directoryLimit = 50)
    val directories = (1..50).map { MockVirtualFile.dir("directory$it") }
    val start = CompletableDeferred<Unit>()
    val received = CompletableDeferred<Unit>()
    val observed = mutableListOf<VirtualFile>()
    val collector = launch {
      requests.batches(Duration.ZERO).collect { batch ->
        observed.addAll(assertInstanceOf(PendingRebuild.Directories::class.java, batch).directoriesToLoad)
        if (observed.size >= directories.size) received.complete(Unit)
      }
    }
    val producers = directories.map { directory ->
      launch(Dispatchers.Default) {
        start.await()
        requests.add(RebuildRequest(setOf(directory), directory.name))
      }
    }
    try {
      start.complete(Unit)
      producers.joinAll()
      received.await()
    }
    finally {
      collector.cancelAndJoin()
    }

    assertThat(observed).containsExactlyInAnyOrderElementsOf(directories)
  }

  @Test
  fun testBatchWaitsForTheQuietPeriod(): Unit = timeoutRunBlocking {
    val requests = PendingRebuildRequests()
    requests.add(RebuildRequest(setOf(first), "pending change"))
    val quietPeriod = 50.milliseconds
    val started = TimeSource.Monotonic.markNow()

    val batch = requests.batches(quietPeriod).first()

    assertThat(started.elapsedNow()).isGreaterThanOrEqualTo(quietPeriod)
    assertThat(assertInstanceOf(PendingRebuild.Directories::class.java, batch).directoriesToLoad).containsExactly(first)
  }

  @Test
  fun testDrainedBatchIsNotEmittedAgain(): Unit = timeoutRunBlocking {
    val requests = PendingRebuildRequests()
    val emitted = Channel<PendingRebuild>(Channel.UNLIMITED)
    val collector = launch {
      requests.batches(10.milliseconds).collect { emitted.send(it) }
    }
    try {
      requests.add(RebuildRequest(setOf(first), "one change"))
      assertThat(assertInstanceOf(PendingRebuild.Directories::class.java, emitted.receive()).directoriesToLoad).containsExactly(first)
      assertThat(withTimeoutOrNull(100.milliseconds) { emitted.receive() }).isNull()
    }
    finally {
      collector.cancelAndJoin()
      emitted.cancel()
    }
  }

  @Test
  fun testCancellationPreservesWorkPendingDuringABuild(): Unit = timeoutRunBlocking {
    val requests = PendingRebuildRequests()
    val buildStarted = CompletableDeferred<PendingRebuild>()
    requests.add(RebuildRequest(setOf(first), "first build"))
    val collector = launch {
      requests.batches(10.milliseconds).collect { batch ->
        buildStarted.complete(batch)
        awaitCancellation()
      }
    }
    try {
      assertThat(assertInstanceOf(PendingRebuild.Directories::class.java, buildStarted.await()).directoriesToLoad).containsExactly(first)
      requests.add(RebuildRequest(setOf(second), "next build"))
    }
    finally {
      collector.cancelAndJoin()
    }

    val batch = requests.batches(Duration.ZERO).first()
    assertThat(assertInstanceOf(PendingRebuild.Directories::class.java, batch).directoriesToLoad).containsExactly(second)
    assertThat(batch.reason).isEqualTo("next build")
  }
}
