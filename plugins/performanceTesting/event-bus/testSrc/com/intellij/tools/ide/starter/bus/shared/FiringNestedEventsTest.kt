package com.intellij.tools.ide.starter.bus.shared

import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.starter.bus.shared.events.SharedEvent

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

class FiringNestedEventsTest : SharedEventsTest() {
  class FirstEvent : SharedEvent()

  class SecondEvent : SharedEvent()

  @Test
  fun `firing nested events should work`() {
    val firstSubscriberProcessedEvent = AtomicBoolean(false)
    val secondSubscriberProcessedEvent = AtomicBoolean(false)

    EventsBus
      .subscribe(this) { _: FirstEvent ->
        firstSubscriberProcessedEvent.set(true)
        EventsBus.postAndWaitProcessing(SecondEvent())
      }
      .subscribe(this) { _: SecondEvent ->
        secondSubscriberProcessedEvent.set(true)
      }

    EventsBus.postAndWaitProcessing(FirstEvent())

    assertTrue(firstSubscriberProcessedEvent.get())
    assertTrue(secondSubscriberProcessedEvent.get())
  }
}