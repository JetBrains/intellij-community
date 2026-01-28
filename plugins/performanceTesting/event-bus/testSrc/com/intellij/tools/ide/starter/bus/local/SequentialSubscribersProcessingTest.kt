// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.bus.local

import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.starter.bus.events.Event
import kotlinx.coroutines.delay
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.milliseconds

class SequentialSubscribersProcessingTest {
  @AfterEach
  fun tearDown() {
    EventsBus.unsubscribeAll()
  }

  @Test
  fun `sequential subscriber are not executed in parallel and executed in the order they were subscribed`() {
    val currentSequentialProcessors = AtomicInteger(0)
    val executed = AtomicInteger(0)
    val nExecutions = 10

    repeat(nExecutions) {
      EventsBus
        .subscribe<Event>(subscriber = "first $it", sequential = true) { _: Event ->
          assertTrue { executed.getAndIncrement() == it }
          assertTrue { currentSequentialProcessors.incrementAndGet() == 1 }
          delay(200)
          assertTrue { currentSequentialProcessors.decrementAndGet() == 0 }
        }
      EventsBus.subscribe<Event>(subscriber = "second $it") { _: Event ->
        assertTrue { executed.get() >= nExecutions } //executed was increased by sequential subscribers that are run in the begining
        executed.incrementAndGet()
        assertTrue { currentSequentialProcessors.get() == 0 }
      }
    }

    EventsBus.postAndWaitProcessing(Event(), ignoreExceptions = false)
    assertEquals(nExecutions * 2, executed.get())
  }

  @Test
  fun `timeout in first sequential subscriber does not stop next sequential subscriber`() {
    var secondExecuted = false

    EventsBus
      .subscribe<Event>(subscriber = "first", timeout = 100.milliseconds, sequential = true) { _: Event ->
        // Emulate long work to trigger timeout
        delay(500)
      }
      .subscribe<Event>(subscriber = "second", sequential = true) { _: Event ->
        secondExecuted = true
      }

    assertThrows<IllegalArgumentException> {
      EventsBus.postAndWaitProcessing(Event(), ignoreExceptions = false)
    }

    assertTrue(secondExecuted, "Second sequential subscriber should still execute after the first times out")
  }

  @Test
  fun `exceptions from multiple sequential subscribers are aggregated and all sequential subscribers executed`() {
    var firstExecuted = false
    var secondExecuted = false

    EventsBus
      .subscribe<Event>(subscriber = "first-err", sequential = true) { _: Event ->
        firstExecuted = true
        throw IllegalStateException("First sequential failed")
      }
      .subscribe<Event>(subscriber = "second-err", sequential = true) { _: Event ->
        secondExecuted = true
        throw IllegalArgumentException("Second sequential failed")
      }

    val ex = assertThrows<IllegalArgumentException> {
      EventsBus.postAndWaitProcessing(Event(), ignoreExceptions = false)
    }

    assertTrue(firstExecuted && secondExecuted, "Both sequential subscribers must be executed even if the first fails")
    assertTrue(ex.message!!.contains("First sequential failed"))
    assertTrue(ex.message!!.contains("Second sequential failed"))
  }

  @Test
  fun `subscribeOnce with sequential executes only once across multiple posts`() {
    var onceCounter = 0
    var regularCounter = 0

    EventsBus
      .subscribeOnce<Event>(subscriber = "once-sequential", sequential = true) { _: Event ->
        onceCounter++
      }
      .subscribe<Event>(subscriber = "regular-sequential", sequential = true) { _: Event ->
        regularCounter++
      }

    repeat(3) {
      EventsBus.postAndWaitProcessing(Event(), ignoreExceptions = false)
    }

    assertTrue(onceCounter == 1, "onceCounter should be 1")
    assertTrue(regularCounter == 3, "regularCounter should be 3")
  }
}
