package com.intellij.tools.ide.starter.bus.local

import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.starter.bus.events.Event
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

class FiringNestedEventsTest {
  @AfterEach
  fun afterEach() {
    EventsBus.unsubscribeAll()
  }

  class FirstEvent : Event() {
    init {
      EventsBus.postAndWaitProcessing(SecondEvent())
    }
  }

  class SecondEvent : Event()

  @Test
  fun `firing nested events should work`() {
    val firstSubscriberProcessedEvent = AtomicBoolean(false)
    val secondSubscriberProcessedEvent = AtomicBoolean(false)

    EventsBus
      .subscribe(this) { _: FirstEvent ->
        firstSubscriberProcessedEvent.set(true)
      }
      .subscribe(this) { _: SecondEvent ->
        secondSubscriberProcessedEvent.set(true)
      }

    EventsBus.postAndWaitProcessing(FirstEvent())

    assertTrue(firstSubscriberProcessedEvent.get())
    assertTrue(secondSubscriberProcessedEvent.get())
  }
}