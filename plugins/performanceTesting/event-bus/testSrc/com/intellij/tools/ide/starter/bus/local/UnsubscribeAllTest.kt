package com.intellij.tools.ide.starter.bus.local

import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.starter.bus.events.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds


class UnsubscribeAllTest {
  companion object {
    private val subscriberProcessedEvent = AtomicBoolean(false)
  }


  @BeforeEach
  fun enableLogs() {
    System.setProperty("eventbus.debug", "true")
  }

  @AfterEach
  fun afterEach() {
    EventsBus.unsubscribeAll()
    assertTrue(subscriberProcessedEvent.get())
    System.setProperty("eventbus.debug", "false")
  }

  class CustomEvent : Event()

  @Test
  fun `wait for postAndWait even in unsubscribeAll if running async`() {
    val subscriberDelay = 4.seconds
    val latch = CountDownLatch(1)

    EventsBus
      .subscribe("First") { _: CustomEvent ->
        val start = System.currentTimeMillis()
        latch.countDown()
        println("Count down")
        while (System.currentTimeMillis() - start < subscriberDelay.inWholeMilliseconds) {
        }
        println("Finished task")
        subscriberProcessedEvent.set(true)
      }

    CoroutineScope(Dispatchers.Default).launch {
      EventsBus.postAndWaitProcessing(CustomEvent())
    }
    latch.await()
  }

}