package com.intellij.ide.starter.junit5.helpers

/**
 * Test-only helper: spins in a sleep loop until killed externally.
 */
object SleepingApp {
  @JvmStatic
  fun main(args: Array<String>) {
    while (true) {
      try {
        Thread.sleep(60_000)
      }
      catch (_: InterruptedException) {
      }
    }
  }
}
