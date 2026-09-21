// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject

import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.pyproject.model.internal.platformBridge.PendingRebuild
import com.intellij.python.pyproject.model.internal.platformBridge.PendingRebuildRequests
import com.intellij.python.pyproject.model.internal.platformBridge.RebuildRequest
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

@Subsystems.IDE
@Layers.Functional
@Timeout(30)
internal class PendingRebuildRequestsTest {
  private val first = MockVirtualFile.dir("first")
  private val second = MockVirtualFile.dir("second")
  private val third = MockVirtualFile.dir("third")

  @Test
  fun testRequestsBeforeCollectionMergeWithoutCountingDuplicates(): Unit = timeoutRunBlocking {
    val requests = PendingRebuildRequests(directoryLimit = 2)
    requests.add(RebuildRequest(setOf(first), "first request"))
    requests.add(RebuildRequest(setOf(first, second), "latest request"))

    val batch = requests.batches(Duration.ZERO).first()
    assertThat(batch.directoriesToLoad).containsExactlyInAnyOrder(first, second)
    assertThat(batch.reloadProjectRoots).isFalse()
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
    assertThat(batch.reloadProjectRoots).isTrue()
    assertThat(batch.directoriesToLoad).isEmpty()

    requests.add(RebuildRequest(setOf(second), "a change during the full scan"))
    val next = requests.batches(Duration.ZERO).first()
    assertThat(next.reloadProjectRoots).isFalse()
    assertThat(next.directoriesToLoad).containsExactly(second)
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
    assertThat(processing.directoriesToLoad).containsExactly(first)
    val next = batches.last()
    assertThat(next.reloadProjectRoots).isEqualTo(overflow)
    if (overflow) {
      assertThat(next.directoriesToLoad).isEmpty()
    }
    else {
      assertThat(next.directoriesToLoad).containsExactlyInAnyOrder(first, second)
      assertThat(next.reason).isEqualTo("latest change during the build")
    }
  }

  @Test
  fun testContentChangesRequestAnotherBuildWithoutDirectories(): Unit = timeoutRunBlocking {
    val requests = PendingRebuildRequests()
    repeat(2) {
      requests.add(RebuildRequest(emptySet(), "content changed"))
      val batch = requests.batches(Duration.ZERO).first()
      assertThat(batch.directoriesToLoad).isEmpty()
      assertThat(batch.reloadProjectRoots).isFalse()
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
        assertThat(batch.reloadProjectRoots).isFalse()
        observed.addAll(batch.directoriesToLoad)
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
    assertThat(batch.directoriesToLoad).containsExactly(first)
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
      assertThat(emitted.receive().directoriesToLoad).containsExactly(first)
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
      assertThat(buildStarted.await().directoriesToLoad).containsExactly(first)
      requests.add(RebuildRequest(setOf(second), "next build"))
    }
    finally {
      collector.cancelAndJoin()
    }

    val batch = requests.batches(Duration.ZERO).first()
    assertThat(batch.directoriesToLoad).containsExactly(second)
    assertThat(batch.reason).isEqualTo("next build")
  }
}
