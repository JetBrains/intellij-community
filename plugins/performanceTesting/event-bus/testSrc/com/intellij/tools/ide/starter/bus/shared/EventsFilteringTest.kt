package com.intellij.tools.ide.starter.bus.shared

import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.starter.bus.shared.events.SharedEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

class EventsFilteringTest : SharedEventsTest() {
  private var isEventProcessed: AtomicBoolean = AtomicBoolean(false)

  @BeforeEach
  fun beforeEach() {
    isEventProcessed.set(false)
  }


  class CustomSignal : SharedEvent()
  class BeforeSignal : SharedEvent()
  class AfterSignal : SharedEvent()
  class AnotherCustomSignal : SharedEvent()

  @RepeatedTest(value = 200)
  fun `filtering events by type is working`() {
    EventsBus.subscribe(this) { _: SharedEvent ->
      isEventProcessed.set(true)
    }

    EventsBus.postAndWaitProcessing(CustomSignal())
    checkIsEventProcessed(false) { isEventProcessed.get() }

    EventsBus.postAndWaitProcessing(SharedEvent())
    checkIsEventProcessed(true) { isEventProcessed.get() }
  }

  @RepeatedTest(value = 100)
  fun `single event is published`() {
    EventsBus.subscribe(this) { _: SharedEvent ->
      isEventProcessed.set(true)
    }

    EventsBus.postAndWaitProcessing(SharedEvent())
    checkIsEventProcessed(true) { isEventProcessed.get() }
  }

  @RepeatedTest(value = 100)
  fun `multiple same events is published and handled by subscribers`() {
    val firstSubscriberInvocationsData = mutableSetOf<Any>()
    val secondSubscriberInvocationsData = mutableSetOf<Any>()

    EventsBus
      .subscribe(this) { event: SharedEvent -> firstSubscriberInvocationsData.add(event) }
      .subscribe(this) { event: SharedEvent -> secondSubscriberInvocationsData.add(event) }

    val firstSignal = BeforeSignal()
    EventsBus.postAndWaitProcessing(firstSignal)
    EventsBus.postAndWaitProcessing(CustomSignal())
    val secondSignal = AfterSignal()
    EventsBus.postAndWaitProcessing(secondSignal)
    EventsBus.postAndWaitProcessing(AnotherCustomSignal())

    firstSubscriberInvocationsData.containsAll(listOf(firstSignal, secondSignal))
    secondSubscriberInvocationsData.containsAll(listOf(firstSignal, secondSignal))
  }

  @Test
  fun `filtering custom events in subscribe`() {
    EventsBus.subscribe(this) { _: CustomSignal ->
      isEventProcessed.set(true)
    }

    repeat(5) {
      EventsBus.postAndWaitProcessing(BeforeSignal())
      EventsBus.postAndWaitProcessing(AfterSignal())
      checkIsEventProcessed(shouldEventBeProcessed = false) { isEventProcessed.get() }
    }

    EventsBus.postAndWaitProcessing(CustomSignal())
    checkIsEventProcessed(shouldEventBeProcessed = true) { isEventProcessed.get() }
  }
}