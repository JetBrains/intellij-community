package com.intellij.ide.starter.tests.unit

import com.intellij.ide.starter.bus.Signal
import com.intellij.ide.starter.bus.StarterBus
import com.intellij.ide.starter.bus.StarterListener
import com.intellij.ide.starter.bus.subscribe
import io.kotest.assertions.timing.eventually
import io.kotest.assertions.withClue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.RepeatedTest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class EventBusTest {
  private var isEventHappened: AtomicBoolean = AtomicBoolean(false)

  private fun checkIsEventFired(shouldEventBeFired: Boolean, isEventFiredGetter: () -> Boolean) {
    val shouldNotMessage = if (!shouldEventBeFired) "NOT" else ""

    runBlocking {
      eventually(duration = 2.seconds, poll = 200.milliseconds) {
        withClue("Event should $shouldNotMessage be fired in 2 sec") {
          isEventFiredGetter() == shouldEventBeFired
        }
      }
    }
  }

  @BeforeEach
  fun beforeEach() {
    isEventHappened.set(false)
  }

  @AfterEach
  fun afterEach() {
    StarterListener.unsubscribe()
  }

  @Disabled("Is not stable")
  @RepeatedTest(value = 50)
  fun `filtering events by type is working`() {
    StarterListener.subscribe { event: Signal ->
      isEventHappened.set(true)
    }

    StarterBus.post(2)
    // make sure there is no side effects
    runBlocking { delay(1.seconds) }

    checkIsEventFired(false) { isEventHappened.get() }

    StarterBus.post(Signal())
    checkIsEventFired(true) { isEventHappened.get() }
  }
}