package com.intellij.tools.ide.starter.bus.local

import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.starter.bus.events.Event
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger


class SubscribingOnlyOnceTest {
  @AfterEach
  fun afterEach() {
    EventsBus.unsubscribeAll()
  }

  @Test
  fun `multiple subscription should not work if subscribed only once`() {
    val obj = Any()

    val eventProcessedTimes = AtomicInteger()
    val secondProcessedTimes = AtomicInteger()

    EventsBus
      .subscribe(this) { _: Event ->
        eventProcessedTimes.incrementAndGet()
      }
      .subscribe(this) { _: Event ->
        eventProcessedTimes.incrementAndGet()
      }
      .subscribe(obj) { _: Event ->
        secondProcessedTimes.incrementAndGet()
      }
      .subscribe(obj) { _: Event ->
        secondProcessedTimes.incrementAndGet()
      }

    EventsBus.postAndWaitProcessing(Event())

    assertEquals(eventProcessedTimes.get(), 1)
    assertEquals(secondProcessedTimes.get(), 1)

    eventProcessedTimes.set(0)
    secondProcessedTimes.set(0)

    EventsBus.postAndWaitProcessing(Event())


    assertEquals(eventProcessedTimes.get(), 1)
    assertEquals(secondProcessedTimes.get(), 1)
  }
}