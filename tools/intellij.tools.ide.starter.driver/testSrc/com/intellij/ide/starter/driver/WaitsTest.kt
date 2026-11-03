package com.intellij.ide.starter.driver

import com.intellij.driver.sdk.WaitForException
import com.intellij.driver.sdk.waitAny
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForOne
import com.intellij.driver.sdk.waitNotNull
import com.intellij.driver.sdk.waitNotNullAsync
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal class WaitsTest {
  @Test
  fun `waitFor returns the observation that satisfied the checker without checking it again`() {
    // a checker that reads remote state, such as whether a connection is still the current one, can change its answer
    val checks = AtomicInteger()
    val observation = Any()

    val result = waitFor(timeout = 5.seconds, getter = { observation }, checker = { checks.incrementAndGet() == 1 })

    assertSame(observation, result)
    assertEquals(1, checks.get())
  }

  @Test
  fun `waitFor checks each new observation once until one satisfies the checker`() {
    val reads = AtomicInteger()
    val checks = AtomicInteger()

    val result = waitFor(timeout = 5.seconds, interval = 1.milliseconds,
                         getter = { reads.incrementAndGet() },
                         checker = { checks.incrementAndGet(); it == 3 })

    assertEquals(3, result)
    assertEquals(3, checks.get())
  }

  @Test
  fun `waitFor fails with the last observation once the timeout has passed`() {
    val checks = AtomicInteger()

    val failure = assertThrows<WaitForException> {
      waitFor<String>(message = "connection starts", timeout = Duration.ZERO,
                      getter = { "starting" }, checker = { checks.incrementAndGet() > 1 })
    }

    assertEquals(1, checks.get())
    assertEquals("Timeout(0s): connection starts. Actual: starting", failure.message)
  }

  @Test
  fun `waitAny returns the elements that satisfied the checker in the successful observation`() {
    // each element satisfies the checker only the first time it is asked, as a remote state that changes would
    val checked = mutableSetOf<String>()

    val result = waitAny(timeout = 5.seconds, getter = { listOf("a", "b") }, checker = { checked.add(it) })

    assertEquals(listOf("a", "b"), result)
  }

  @Test
  fun `waitAny fails with the not suitable elements of the last observation`() {
    val failure = assertThrows<WaitForException> {
      waitAny<String>(message = "a tab opens", timeout = Duration.ZERO, getter = { listOf("a", "b") }, checker = { false })
    }

    assertEquals("Timeout(0s): a tab opens: Await any\n\tnot suitable:\n\t\ta\n\t\tb", failure.message)
  }

  @Test
  fun `waitForOne returns the element that satisfied the checker in the successful observation`() {
    val checked = mutableSetOf<String>()

    val result = waitForOne(timeout = 5.seconds, getter = { listOf("a", "b") }, checker = { it == "b" && checked.add(it) })

    assertEquals("b", result)
  }

  @Test
  fun `waitForOne fails with the suitable and not suitable elements of the last observation`() {
    val failure = assertThrows<WaitForException> {
      waitForOne<String>(message = "one tab", timeout = Duration.ZERO, getter = { listOf("a", "b", "c") }, checker = { it != "b" })
    }

    assertEquals("Timeout(0s): one tab: Await one" +
                 "\n\tsuitable:\n\t\ta\n\t\tc" +
                 "\n\tnot suitable:\n\t\tb", failure.message)
  }

  @Test
  fun `waitNotNullAsync fails with its errorMessage when the last observation is null`() {
    val failure = assertThrows<WaitForException> {
      runBlocking {
        waitNotNullAsync<String>(timeout = 50.milliseconds, interval = 10.milliseconds,
                                 errorMessage = { "no project, last value: $it" }, getter = { null })
      }
    }

    assertEquals("Timeout(50ms): no project, last value: null", failure.message)
  }

  @Test
  fun `waitFor of a condition fails with the message only`() {
    val failure = assertThrows<WaitForException> {
      waitFor(message = "indexing ends", timeout = Duration.ZERO) { false }
    }

    assertEquals("Timeout(0s): indexing ends", failure.message)
  }

  @Test
  fun `waitNotNull fails with the message only`() {
    val failure = assertThrows<WaitForException> {
      waitNotNull<String>(message = "project opens", timeout = Duration.ZERO) { null }
    }

    assertEquals("Timeout(0s): project opens: Await not null", failure.message)
  }

  @Test
  fun `waitAny without a message fails without null in the error`() {
    val failure = assertThrows<WaitForException> {
      waitAny<String>(timeout = Duration.ZERO, getter = { emptyList() })
    }

    assertEquals("Timeout(0s): Await any\n\tnot suitable:\n\t\tnone", failure.message)
  }

  @Test
  fun `waitNotNullAsync fails with the same text as waitNotNull`() {
    val failure = assertThrows<WaitForException> {
      runBlocking {
        waitNotNullAsync<String>(message = "project opens", timeout = 50.milliseconds, interval = 10.milliseconds) { null }
      }
    }

    assertEquals("Timeout(50ms): project opens: Await not null", failure.message)
  }

  @Test
  fun `waitFor of a condition without a message fails with the timeout only`() {
    val failure = assertThrows<WaitForException> {
      waitFor(timeout = Duration.ZERO) { false }
    }

    assertEquals("Timeout(0s)", failure.message)
  }

  @Test
  fun `waitAny cuts a long list in the error to the length of the log`() {
    val elements = (1..100).map { "element-$it" }

    val failure = assertThrows<WaitForException> {
      waitAny(message = "a tab opens", timeout = Duration.ZERO, getter = { elements }, checker = { false })
    }

    assertEquals("Timeout(0s): a tab opens: Await any\n\tnot suitable:\n\t\t" + elements.joinToString("\n\t\t").take(600) + "...",
                 failure.message)
  }
}
