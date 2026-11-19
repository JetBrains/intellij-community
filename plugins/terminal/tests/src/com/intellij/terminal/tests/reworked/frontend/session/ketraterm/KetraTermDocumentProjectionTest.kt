// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session.ketraterm

import com.intellij.terminal.frontend.session.ketraterm.collectKetraTermProjections
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class KetraTermDocumentProjectionTest {
  @Test
  fun `output requests coalesce without native publication`() = runTest {
    val requests = Channel<Unit>(Channel.CONFLATED)
    val calls = ArrayList<Boolean>()
    val job = backgroundScope.launch {
      requests.collectKetraTermProjections { visible ->
        calls.add(visible)
        visible
      }
    }
    requests.trySend(Unit)
    runCurrent()
    repeat(100) { requests.trySend(Unit) }
    advanceTimeBy(16)
    runCurrent()
    assertThat(calls).containsExactly(true)
    advanceTimeBy(8)
    runCurrent()
    assertThat(calls).containsExactly(true, false)
    advanceTimeBy(100)
    runCurrent()
    assertThat(calls).hasSize(2)
    job.cancelAndJoin()
    requests.close()
  }

  @Test
  fun `cancellation stops pending document history`() = runTest {
    val requests = Channel<Unit>(Channel.CONFLATED)
    val calls = ArrayList<Boolean>()
    val job = backgroundScope.launch {
      requests.collectKetraTermProjections { visible ->
        calls.add(visible)
        true
      }
    }
    requests.trySend(Unit)
    runCurrent()
    advanceTimeBy(16)
    runCurrent()
    assertThat(calls).containsExactly(true)
    job.cancelAndJoin()
    requests.trySend(Unit)
    advanceTimeBy(100)
    runCurrent()
    assertThat(calls).containsExactly(true)
    requests.close()
  }

  @Test
  fun `continuous output completes pending history`() = runTest {
    val requests = Channel<Unit>(Channel.CONFLATED)
    val calls = ArrayList<Boolean>()
    val projection = backgroundScope.launch {
      requests.collectKetraTermProjections(inputPending = { true }) { visible ->
        calls.add(visible)
        true
      }
    }
    val producer = backgroundScope.launch {
      while (true) {
        requests.trySend(Unit)
        delay(1)
      }
    }
    advanceTimeBy(300)
    runCurrent()
    assertThat(calls).contains(false)
    assertThat(calls.count { it }).isLessThan(20)
    producer.cancelAndJoin()
    projection.cancelAndJoin()
    requests.close()
  }

  @Test
  fun `a request during projection remains pending`() = runTest {
    val requests = Channel<Unit>(Channel.CONFLATED)
    var calls = 0
    val job = backgroundScope.launch {
      requests.collectKetraTermProjections {
        calls++
        if (calls < 3) requests.trySend(Unit)
        false
      }
    }
    requests.trySend(Unit)
    advanceTimeBy(48)
    runCurrent()
    assertThat(calls).isEqualTo(3)
    job.cancelAndJoin()
    requests.close()
  }
}
