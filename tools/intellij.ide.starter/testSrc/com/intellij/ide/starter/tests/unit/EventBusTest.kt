package com.intellij.ide.starter.tests.unit

import com.intellij.ide.starter.bus.*
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration

class EventBusTest {
  private val receiver = EventsReceiver()
  private var isEventHappened: AtomicBoolean = AtomicBoolean(false)

  private fun checkIsEventFired(shouldEventBeFired: Boolean, isEventFiredGetter: () -> Boolean) {
    val shouldNotMessage = if (!shouldEventBeFired) "NOT" else ""

    withClue("Event should $shouldNotMessage be fired") {
      runBlocking {
        try {
          withTimeout(timeout = Duration.seconds(5)) {
            while (shouldEventBeFired != isEventFiredGetter()) {
              delay(Duration.milliseconds(500))
            }
          }
        }
        catch (_: Exception) {
        }
      }

      isEventFiredGetter().shouldBe(shouldEventBeFired)
    }
  }

  @Before
  fun beforeEach() {
    isEventHappened.set(false)
  }

  @After
  fun afterEach() {
    receiver.unsubscribe()
  }

  @Test
  fun filteringEventsByTypeIsWorking() {
    receiver.subscribe { event: Int ->
      isEventHappened.set(true)
    }

    EventBus.post(Signal<EventBusTest>(EventTimelineState.READY))
    checkIsEventFired(false) { isEventHappened.get() }

    EventBus.post(2)
    checkIsEventFired(true) { isEventHappened.get() }
  }
}