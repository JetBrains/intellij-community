package com.intellij.ide.starter.driver

import com.intellij.driver.sdk.WaitForException
import com.intellij.driver.sdk.waitAny
import com.intellij.driver.sdk.waitFor
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
    assertEquals("Timeout(0s): Failed: connection starts. Actual: starting", failure.message)
  }

  @Test
  fun `waitAny returns the elements that satisfied the checker in the successful observation`() {
    // each element satisfies the checker only the first time it is asked, as a remote state that changes would
    val checked = mutableSetOf<String>()

    val result = waitAny(timeout = 5.seconds, getter = { listOf("a", "b") }, checker = { checked.add(it) })

    assertEquals(listOf("a", "b"), result)
  }
}
