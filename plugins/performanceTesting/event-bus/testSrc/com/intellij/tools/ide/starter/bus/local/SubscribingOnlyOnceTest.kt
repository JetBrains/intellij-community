package com.intellij.tools.ide.starter.bus.local

import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.starter.bus.events.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds


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

  @Test
  fun `subscribe is executed many times when triggered from different threads`() {
    val obj = Any()

    val eventProcessedTimes = AtomicInteger()

    EventsBus.subscribe(obj) { _: Event ->
      eventProcessedTimes.incrementAndGet()
    }

    runBlocking {
      val n = 10
      launch {
        repeat(n) {
          launch(Dispatchers.IO) {
            EventsBus.postAndWaitProcessing(Event())
          }
        }
      }.join()

      delay(2.seconds)
      assertEquals(eventProcessedTimes.get(), n)

      EventsBus.postAndWaitProcessing(Event())

      assertEquals(eventProcessedTimes.get(), n + 1)
    }
  }

  @Test
  fun `subscribe once is executed once`() {
    val obj = Any()

    val eventProcessedTimes = AtomicInteger()
    val secondProcessedTimes = AtomicInteger()

    EventsBus
      .subscribeOnce(this) { _: Event ->
        eventProcessedTimes.incrementAndGet()
      }
      .subscribe(obj) { _: Event ->
        secondProcessedTimes.incrementAndGet()
      }
    runBlocking {
      val n = 10
      launch {
        repeat(n) {
          launch(Dispatchers.IO) {
            EventsBus.postAndWaitProcessing(Event())
          }
        }
      }.join()

      assertEquals(1, eventProcessedTimes.get())
      assertEquals(n, secondProcessedTimes.get())

      EventsBus.postAndWaitProcessing(Event())

      assertEquals(1, eventProcessedTimes.get())
      assertEquals(n + 1, secondProcessedTimes.get())
    }
  }

  @Test
  fun `subscribe once and subscribe once again`() {
    val eventProcessedTimes = AtomicInteger()

    runBlocking {
      EventsBus.subscribeOnce(this) { _: Event -> eventProcessedTimes.incrementAndGet() }
      val n = 10
      launch {
        repeat(n) {
          launch(Dispatchers.IO) {
            EventsBus.postAndWaitProcessing(Event())
          }
        }
      }.join()
      assertEquals(1, eventProcessedTimes.get())

      eventProcessedTimes.set(0)
      EventsBus.subscribeOnce(this) { _: Event -> eventProcessedTimes.incrementAndGet() }
      launch {
        repeat(n) {
          launch(Dispatchers.IO) {
            EventsBus.postAndWaitProcessing(Event())
          }
        }
      }.join()
      assertEquals(1, eventProcessedTimes.get())
    }
  }

  @Test
  fun `subscribe once while adding events`() {
    val eventProcessedTimes = AtomicInteger()

    runBlocking {
      launch {
        repeat(10) {
          launch(Dispatchers.IO) {
            EventsBus.postAndWaitProcessing(Event())
          }
        }
        launch { EventsBus.subscribeOnce(this) { _: Event -> eventProcessedTimes.incrementAndGet() } }
      }.join()
      EventsBus.postAndWaitProcessing(Event())

      assertEquals(1, eventProcessedTimes.get())
    }
  }
}