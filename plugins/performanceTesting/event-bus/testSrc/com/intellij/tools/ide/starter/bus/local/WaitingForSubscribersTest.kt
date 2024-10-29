package com.intellij.tools.ide.starter.bus.local

import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.starter.bus.events.Event
import kotlinx.coroutines.delay
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.RepeatedTest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime


class WaitingForSubscribersTest {

  @AfterEach
  fun afterEach() {
    EventsBus.unsubscribeAll()
  }

  class CustomEvent : Event()

  @RepeatedTest(value = 10)
  fun `waiting till subscribers finish their work`() {
    val firstSubscriberProcessedEvent = AtomicBoolean(false)
    val secondSubscriberProcessedEvent = AtomicBoolean(false)

    val firstSubscriberDelay = 2.seconds
    val secondSubscriberDelay = 4.seconds

    EventsBus
      .subscribe("First") { _: Event ->
        delay(firstSubscriberDelay)
        firstSubscriberProcessedEvent.set(true)
      }
      .subscribe("Second") { _: Event ->
        delay(secondSubscriberDelay)
        secondSubscriberProcessedEvent.set(true)
      }

    val timeout = 6.seconds

    // First event should not be processed by subscribers. Method should complete without waiting
    val firstEventDuration = measureTime {
      EventsBus.postAndWaitProcessing(CustomEvent())
    }
    checkIsEventProcessed(false) { firstSubscriberProcessedEvent.get() }
    checkIsEventProcessed(false) { secondSubscriberProcessedEvent.get() }
    assertTrue(firstEventDuration < 100.milliseconds)


    val secondEventDuration = measureTime {
      EventsBus.postAndWaitProcessing(Event())
    }
    checkIsEventProcessed(true) { firstSubscriberProcessedEvent.get() }
    checkIsEventProcessed(true) { secondSubscriberProcessedEvent.get() }
    assertTrue(secondEventDuration < timeout)
    assertTrue(secondEventDuration >= secondSubscriberDelay)
  }

  @RepeatedTest(value = 5)
  fun `unsuccessful awaiting of subscribers`() {
    val firstSubscriberProcessedEvent = AtomicBoolean(false)
    val secondSubscriberProcessedEvent = AtomicBoolean(false)
    val gotException = AtomicBoolean(false)

    val firstSubscriberDelay = 4.seconds
    val secondSubscriberDelay = 6.seconds
    val timeout = 2.seconds

    EventsBus
      .subscribe(firstSubscriberProcessedEvent, timeout) { _: Event ->
        delay(firstSubscriberDelay)
        firstSubscriberProcessedEvent.set(true)
      }
      .subscribe(secondSubscriberProcessedEvent, timeout) { _: Event ->
        delay(secondSubscriberDelay)
        secondSubscriberProcessedEvent.set(true)
      }


    val duration = measureTime {
      try {
        EventsBus.postAndWaitProcessing(Event(), ignoreExceptions = false)
      }
      catch (e: Throwable) {
        gotException.set(true)
      }
    }

    assertFalse(firstSubscriberProcessedEvent.get())
    assertFalse(secondSubscriberProcessedEvent.get())

    assertTrue(gotException.get())
    assertTrue(duration >= timeout)
    assertTrue(duration < timeout.plus(1.seconds))
  }

  @RepeatedTest(value = 5)
  fun `should process all subscribers even if exception occurs in one subscriber`() {
    val subscriberProcessedEvent = AtomicBoolean(false)
    val gotException = AtomicBoolean(false)

    val firstSubscriberDelay = 1.seconds
    val secondSubscriberDelay = 3.seconds

    EventsBus
      .subscribe("First") { _: Event ->
        delay(firstSubscriberDelay)
        throw IllegalStateException("Exception")
      }
      .subscribe("Second") { _: Event ->
        delay(secondSubscriberDelay)
        subscriberProcessedEvent.set(true)
      }

    val eventDuration = measureTime {
      try {
        EventsBus.postAndWaitProcessing(Event(), ignoreExceptions = false)
      }
      catch (e: Throwable) {
        gotException.set(true)
      }
    }

    assertTrue(gotException.get())
    checkIsEventProcessed(false) { subscriberProcessedEvent.get() }
    assertTrue(eventDuration >= secondSubscriberDelay)
  }
}