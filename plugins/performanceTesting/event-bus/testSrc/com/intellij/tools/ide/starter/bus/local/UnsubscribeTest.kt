package com.intellij.tools.ide.starter.bus.local

import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.starter.bus.events.Event
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class UnsubscribeTest {
  @AfterEach
  fun afterEach() {
    EventsBus.unsubscribeAll()
  }

  @RepeatedTest(value = 5)
  @Timeout(1, unit = TimeUnit.MINUTES)
  fun `should not call unsubscribed subscriber`() {
    val firstSubscriberProcessedEvent = AtomicInteger(0)
    val secondSubscriberProcessedEvent = AtomicInteger(0)
    val firstSubscriber = "First"
    val secondSubscriber = "Second"
    EventsBus
      .subscribe(firstSubscriber) { _: Event ->
        firstSubscriberProcessedEvent.incrementAndGet()
      }
      .subscribe(secondSubscriber) { _: Event ->
        secondSubscriberProcessedEvent.incrementAndGet()
      }

    EventsBus.postAndWaitProcessing(Event())
    assertEquals(firstSubscriberProcessedEvent.get(), 1)
    assertEquals(secondSubscriberProcessedEvent.get(), 1)

    EventsBus.unsubscribe<Event>(secondSubscriber)

    EventsBus.postAndWaitProcessing(Event())
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
      .subscribe(firstSubscriber) { _: Event ->
        firstSubscriberProcessedEvent.incrementAndGet()
      }
      .subscribe(secondSubscriber) { _: Event ->
        secondSubscriberProcessedEvent.incrementAndGet()
        EventsBus.unsubscribe<Event>(secondSubscriber)
      }

    EventsBus.postAndWaitProcessing(Event())
    assertEquals(firstSubscriberProcessedEvent.get(), 1)
    assertEquals(secondSubscriberProcessedEvent.get(), 1)

    EventsBus.postAndWaitProcessing(Event())
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
      .subscribe(firstSubscriber) { _: Event ->
        firstSubscriberProcessedEvent.incrementAndGet()
      }

    EventsBus.postAndWaitProcessing(Event())
    assertEquals(firstSubscriberProcessedEvent.get(), 1)

    EventsBus.unsubscribe<Event>(secondSubscriber, false)
    EventsBus.postAndWaitProcessing(Event())
    assertEquals(firstSubscriberProcessedEvent.get(), 2)
  }
}