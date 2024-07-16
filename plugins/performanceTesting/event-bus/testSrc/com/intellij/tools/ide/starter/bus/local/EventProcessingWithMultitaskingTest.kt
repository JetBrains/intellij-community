package com.intellij.tools.ide.starter.bus.local

import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.starter.bus.events.Event
import kotlinx.coroutines.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime


class EventProcessingWithMultitaskingTest {
  class YourEvent1 : Event()
  class YourEvent2 : Event()
  class YourEvent3 : Event()

  class YourEventsReceiver

  private val maxTasksNumber = 10
  private val counter = AtomicInteger(0)

  @BeforeEach
  fun beforeEach() {
    counter.set(0)
  }

  @AfterEach
  fun afterEach() {
    EventsBus.unsubscribeAll()
  }

  /** If event is null - new event  */
  private fun runEventProcessingTest(event: Event?, timeout: Duration) = runBlocking {
    val jobs = List(maxTasksNumber) {
      launch(Dispatchers.Default) {
        val eventToFire = event ?: listOf(YourEvent1(), YourEvent2(), YourEvent3()).random()
        val duration = measureTime {
          EventsBus.postAndWaitProcessing(eventToFire)
        }
        println("Processing event ${eventToFire.hashCode()} took $duration")
      }
    }

    withTimeoutOrNull(timeout) {
      jobs.forEach { it.join() }
    } ?: throw AssertionError("Test timed out in $timeout waiting for all events to be processed")

    assertEquals(counter.get(), maxTasksNumber)
  }

  @Test
  fun `awaiting event processing exactly one event`(): Unit = runBlocking {
    EventsBus.subscribe<YourEvent1>(YourEventsReceiver()) {
      delay(500.milliseconds)
      println("Handling event ${counter.incrementAndGet()} times")
    }

    // synchronization in FlowBus for the same event instance makes it slow
    runEventProcessingTest(YourEvent1(), timeout = 7.seconds)
  }

  @Test
  fun `awaiting event processing on different event reference`(): Unit = runBlocking {
    EventsBus.subscribe<YourEvent1>(YourEventsReceiver()) {
      delay(500.milliseconds)
      println("First handling event ${counter.incrementAndGet()} times")
    }

    EventsBus.subscribe<YourEvent2>(YourEventsReceiver()) {
      delay(500.milliseconds)
      println("Second handling event ${counter.incrementAndGet()} times")
    }

    EventsBus.subscribe<YourEvent3>(YourEventsReceiver()) {
      delay(500.milliseconds)
      println("Second handling event ${counter.incrementAndGet()} times")
    }

    runEventProcessingTest(null, timeout = 5.seconds)
  }
}