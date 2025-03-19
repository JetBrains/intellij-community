package com.intellij.tools.ide.starter.bus.shared

import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.starter.bus.shared.events.SharedEvent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger


class SubscribingOnlyOnceTest : SharedEventsTest()  {
  @Test
  fun `multiple subscription should not work if subscribed only once`() {
    val obj = Any()

    val eventProcessedTimes = AtomicInteger()
    val secondProcessedTimes = AtomicInteger()

    EventsBus
      .subscribe(this) { _: SharedEvent ->
        eventProcessedTimes.incrementAndGet()
      }
      .subscribe(this) { _: SharedEvent ->
        eventProcessedTimes.incrementAndGet()
      }
      .subscribe(obj) { _: SharedEvent ->
        secondProcessedTimes.incrementAndGet()
      }
      .subscribe(obj) { _: SharedEvent ->
        secondProcessedTimes.incrementAndGet()
      }

    EventsBus.postAndWaitProcessing(SharedEvent())

    assertEquals(eventProcessedTimes.get(), 1)
    assertEquals(secondProcessedTimes.get(), 1)

    eventProcessedTimes.set(0)
    secondProcessedTimes.set(0)

    EventsBus.postAndWaitProcessing(SharedEvent())


    assertEquals(eventProcessedTimes.get(), 1)
    assertEquals(secondProcessedTimes.get(), 1)
  }
}