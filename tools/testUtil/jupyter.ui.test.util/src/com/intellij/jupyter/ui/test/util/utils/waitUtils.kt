package com.intellij.jupyter.ui.test.util.utils

import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Specifies a strategy for post-execution update follow-up
 */
enum class PostExecutionAwaitStrategy {
  AwaitHighlighting,
  AwaitNone
}

fun <T : Any> (() -> T).waitFor(
  message: String = "The condition wasn't satisfied within the timeout",
  timeout: Duration = 15.seconds,
  condition: (T) -> Boolean,
): T {
  return waitFor(
    message = message,
    timeout = timeout,
    interval = 500.milliseconds,
    getter = { invoke() },
    checker = condition
  )
}

fun <C : Collection<*>> (() -> C).waitEmpty(message: String = "The list is still not empty"): C =
  this.waitFor(message = message) {
    it.isEmpty()
  }

fun <C : Collection<*>> (() -> C).waitNotEmpty(message: String = "The list is still empty"): C =
  this.waitFor(message = message) {
    it.isNotEmpty()
  }
