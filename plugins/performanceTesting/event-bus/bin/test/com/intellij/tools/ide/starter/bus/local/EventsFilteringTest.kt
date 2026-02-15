package com.intellij.tools.ide.starter.bus.local

import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.starter.bus.events.Event
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal fun checkIsEventProcessed(shouldEventBeProcessed: Boolean, isEventProcessedGetter: () -> Boolean) {
  runBlocking {
    withTimeout(2.seconds) {
      if (isEventProcessedGetter() == shouldEventBeProcessed) return@withTimeout
      delay(50.milliseconds)
    }
  }
}

class EventsFilteringTest {
  private var isEventProcessed: AtomicBoolean = AtomicBoolean(false)

  @BeforeEach
  fun beforeEach() {
    isEventProcessed.set(false)
  }

  @AfterEach
  fun afterEach() {
    EventsBus.unsubscribeAll()
  }

  class CustomEvent : Event()
  class BeforeEvent : Event()
  class AfterEvent : Event()
  class AnotherCustomEvent : Event()

  @RepeatedTest(value = 200)
  fun `filtering events by type is working`() {
    EventsBus.subscribe(this) { _: Event ->
      isEventProcessed.set(true)
    }

    EventsBus.postAndWaitProcessing(CustomEvent())
    checkIsEventProcessed(false) { isEventProcessed.get() }

    EventsBus.postAndWaitProcessing(Event())
    checkIsEventProcessed(true) { isEventProcessed.get() }
  }

  @RepeatedTest(value = 100)
  fun `single event is published`() {
    EventsBus.subscribe(this) { _: Event ->
      isEventProcessed.set(true)
    }

    EventsBus.postAndWaitProcessing(Event())
    checkIsEventProcessed(true) { isEventProcessed.get() }
  }

  @RepeatedTest(value = 100)
  fun `multiple same events is published and handled by subscribers`() {
    val firstSubscriberInvocationsData = mutableSetOf<Any>()
    val secondSubscriberInvocationsData = mutableSetOf<Any>()

    EventsBus
      .subscribe(this) { event: Event -> firstSubscriberInvocationsData.add(event) }
      .subscribe(this) { event: Event -> secondSubscriberInvocationsData.add(event) }

    val firstSignal = BeforeEvent()
    EventsBus.postAndWaitProcessing(firstSignal)
    EventsBus.postAndWaitProcessing(CustomEvent())
    val secondSignal = AfterEvent()
    EventsBus.postAndWaitProcessing(secondSignal)
    EventsBus.postAndWaitProcessing(AnotherCustomEvent())

    firstSubscriberInvocationsData.containsAll(listOf(firstSignal, secondSignal))
    secondSubscriberInvocationsData.containsAll(listOf(firstSignal, secondSignal))
  }

  @Test
  fun `filtering custom events in subscribe`() {
    EventsBus.subscribe(this) { _: CustomEvent ->
      isEventProcessed.set(true)
    }

    repeat(5) {
      EventsBus.postAndWaitProcessing(BeforeEvent())
      EventsBus.postAndWaitProcessing(AfterEvent())
      checkIsEventProcessed(shouldEventBeProcessed = false) { isEventProcessed.get() }
    }

    EventsBus.postAndWaitProcessing(CustomEvent())
    checkIsEventProcessed(shouldEventBeProcessed = true) { isEventProcessed.get() }
  }
}