package com.intellij.tools.ide.starter.bus.shared

import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.starter.bus.shared.events.SharedEvent
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.RepeatedTest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime


class WaitingForSubscribersTest : SharedEventsTest() {
  class CustomSignal : SharedEvent()

  @RepeatedTest(value = 10)
  fun `waiting till subscribers finish their work`() {
    val firstSubscriberProcessedEvent = AtomicBoolean(false)
    val secondSubscriberProcessedEvent = AtomicBoolean(false)

    val firstSubscriberDelay = 2.seconds
    val secondSubscriberDelay = 4.seconds

    EventsBus
      .subscribe("First") { _: SharedEvent ->
        delay(firstSubscriberDelay)
        firstSubscriberProcessedEvent.set(true)
      }
      .subscribe("Second") { _: SharedEvent ->
        delay(secondSubscriberDelay)
        secondSubscriberProcessedEvent.set(true)
      }

    val timeout = 6.seconds

    // First event should not be processed by subscribers. Method should complete without waiting
    val firstEventDuration = measureTime {
      EventsBus.postAndWaitProcessing(CustomSignal())
    }
    checkIsEventProcessed(false) { firstSubscriberProcessedEvent.get() }
    checkIsEventProcessed(false) { secondSubscriberProcessedEvent.get() }
    assertTrue(firstEventDuration < 100.milliseconds)


    val secondEventDuration = measureTime {
      EventsBus.postAndWaitProcessing(SharedEvent())
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

    val firstSubscriberDelay = 4.seconds
    val secondSubscriberDelay = 6.seconds
    val timeout = 2.seconds

    EventsBus
      .subscribe(firstSubscriberProcessedEvent, timeout) { _: SharedEvent ->
        delay(firstSubscriberDelay)
        firstSubscriberProcessedEvent.set(true)
      }
      .subscribe(secondSubscriberProcessedEvent, timeout) { _: SharedEvent ->
        delay(secondSubscriberDelay)
        secondSubscriberProcessedEvent.set(true)
      }


    val duration = measureTime {
      EventsBus.postAndWaitProcessing(SharedEvent())
    }

    assertFalse(firstSubscriberProcessedEvent.get())
    assertFalse(secondSubscriberProcessedEvent.get())

    assertTrue(duration >= timeout)
    assertTrue(duration < timeout.plus(1.seconds))
  }
}