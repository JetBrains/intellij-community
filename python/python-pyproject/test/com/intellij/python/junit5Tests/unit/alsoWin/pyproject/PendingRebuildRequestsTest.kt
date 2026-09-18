// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject

import com.intellij.mock.MockVirtualFile
import com.intellij.python.pyproject.model.internal.platformBridge.PendingRebuildRequests
import com.intellij.python.pyproject.model.internal.platformBridge.RebuildRequest
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@Subsystems.IDE
@Layers.Functional
@Timeout(30)
internal class PendingRebuildRequestsTest {
  private val first = MockVirtualFile.dir("first")
  private val second = MockVirtualFile.dir("second")
  private val third = MockVirtualFile.dir("third")

  @Test
  fun testRepeatedDirectoriesDoNotExceedTheLimit() {
    val requests = PendingRebuildRequests(directoryLimit = 2)
    requests.add(RebuildRequest(setOf(first), "first request"))
    requests.add(RebuildRequest(setOf(first, second), "latest request"))

    val batch = requests.take()!!
    assertThat(batch.directoriesToLoad).containsExactlyInAnyOrder(first, second)
    assertThat(batch.reloadProjectRoots).isFalse()
    assertThat(batch.reason).isEqualTo("latest request")
    assertThat(requests.take()).isNull()
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun testOverflowRequestsAFullScan(singleRequest: Boolean) {
    val requests = PendingRebuildRequests(directoryLimit = 2)
    if (singleRequest) {
      requests.add(RebuildRequest(setOf(first, second, third), "large request"))
    }
    else {
      requests.add(RebuildRequest(setOf(first, second), "at the limit"))
      requests.add(RebuildRequest(setOf(third), "above the limit"))
    }
    requests.add(RebuildRequest(setOf(first), "another change"))

    val batch = requests.take()!!
    assertThat(batch.reloadProjectRoots).isTrue()
    assertThat(batch.directoriesToLoad).isEmpty()

    requests.add(RebuildRequest(setOf(second), "a change during the full scan"))
    val next = requests.take()!!
    assertThat(next.reloadProjectRoots).isFalse()
    assertThat(next.directoriesToLoad).containsExactly(second)
  }

  @Test
  fun testChangesDuringABuildRemainPending() {
    val requests = PendingRebuildRequests(directoryLimit = 2)
    requests.add(RebuildRequest(setOf(first), "before the build"))
    val processing = requests.take()!!

    requests.add(RebuildRequest(setOf(first, second), "during the build"))
    val next = requests.take()!!

    assertThat(processing.directoriesToLoad).containsExactly(first)
    assertThat(next.directoriesToLoad).containsExactlyInAnyOrder(first, second)
    assertThat(next.reason).isEqualTo("during the build")
  }

  @Test
  fun testContentChangesRequestAnotherBuildWithoutDirectories() {
    val requests = PendingRebuildRequests()
    repeat(2) {
      requests.add(RebuildRequest(emptySet(), "content changed"))
      val batch = requests.take()!!
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
    val producers = directories.map { directory ->
      launch(Dispatchers.Default) {
        start.await()
        requests.add(RebuildRequest(setOf(directory), directory.name))
      }
    }
    start.complete(Unit)
    producers.joinAll()

    val batch = requests.take()!!
    assertThat(batch.directoriesToLoad).containsExactlyInAnyOrderElementsOf(directories)
    assertThat(batch.reloadProjectRoots).isFalse()
  }
}
