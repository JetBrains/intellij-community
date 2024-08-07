package com.intellij.tools.ide.starter.bus.shared

import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.starter.bus.shared.events.SharedEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class UnsubscribeTest : SharedEventsTest() {

  @RepeatedTest(value = 5)
  @Timeout(1, unit = TimeUnit.MINUTES)
  fun `should not call unsubscribed subscriber`() {
    val firstSubscriberProcessedEvent = AtomicInteger(0)
    val secondSubscriberProcessedEvent = AtomicInteger(0)
    val firstSubscriber = "First"
    val secondSubscriber = "Second"
    EventsBus
      .subscribe(firstSubscriber) { _: SharedEvent ->
        firstSubscriberProcessedEvent.incrementAndGet()
      }
      .subscribe(secondSubscriber) { _: SharedEvent ->
        secondSubscriberProcessedEvent.incrementAndGet()
      }

    EventsBus.postAndWaitProcessing(SharedEvent())
    assertEquals(firstSubscriberProcessedEvent.get(), 1)
    assertEquals(secondSubscriberProcessedEvent.get(), 1)

    EventsBus.unsubscribe<SharedEvent>(secondSubscriber)

    EventsBus.postAndWaitProcessing(SharedEvent())
    assertEquals(firstSubscriberProcessedEvent.get(), 2)
    assertEquals(secondSubscriberProcessedEvent.get(), 1)
  }

  @RepeatedTest(value = 5)
  @Timeout(1, unit = TimeUnit.MINUTES)
  fun `should unsubscribe correct from subscriber`() {
    val firstSubscriberProcessedEvent = AtomicInteger(0)
    val secondSubscriberProcessedEvent = AtomicInteger(0)
    val firstSubscriber = "First"
    val secondSubscriber = "Second"
    EventsBus
      .subscribe(firstSubscriber) { _: SharedEvent ->
        firstSubscriberProcessedEvent.incrementAndGet()
      }
      .subscribe(secondSubscriber) { _: SharedEvent ->
        secondSubscriberProcessedEvent.incrementAndGet()
        EventsBus.unsubscribe<SharedEvent>(secondSubscriber)
      }

    EventsBus.postAndWaitProcessing(SharedEvent())
    assertEquals(firstSubscriberProcessedEvent.get(), 1)
    assertEquals(secondSubscriberProcessedEvent.get(), 1)

    EventsBus.postAndWaitProcessing(SharedEvent())
    assertEquals(firstSubscriberProcessedEvent.get(), 2)
    assertEquals(secondSubscriberProcessedEvent.get(), 1)
  }

  @RepeatedTest(value = 5)
  @Timeout(1, unit = TimeUnit.MINUTES)
  fun `should unsubscribe non existing subscriber without exception`() {
    val firstSubscriberProcessedEvent = AtomicInteger(0)
    val firstSubscriber = "First"
    val secondSubscriber = "Second"
    EventsBus
      .subscribe(firstSubscriber) { _: SharedEvent ->
        firstSubscriberProcessedEvent.incrementAndGet()
      }

    EventsBus.postAndWaitProcessing(SharedEvent())
    assertEquals(firstSubscriberProcessedEvent.get(), 1)

    EventsBus.unsubscribe<SharedEvent>(secondSubscriber, false)
    EventsBus.postAndWaitProcessing(SharedEvent())
    assertEquals(firstSubscriberProcessedEvent.get(), 2)
  }
}