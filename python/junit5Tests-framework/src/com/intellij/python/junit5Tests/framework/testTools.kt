package com.intellij.python.junit5Tests.framework

import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds


/**
 * Awaits for [kotlin.code] (i.e [org.junit.jupiter.api.Assertions.assertEquals]) doesn't throw [kotlin.AssertionError]
 */
suspend fun waitNoError(delay: Duration = 100.milliseconds, repeat: Int = 50, checkCondition: suspend () -> Unit) {
  repeat(repeat) {
    try {
      checkCondition()
      return
    }
    catch (_: AssertionError) {
      false
      delay(delay)
    }
  }
  checkCondition()
}