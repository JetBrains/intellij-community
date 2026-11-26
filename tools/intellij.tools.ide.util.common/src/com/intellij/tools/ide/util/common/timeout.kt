package com.intellij.tools.ide.util.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Blocks the current thread until [action] completes in [context].
 *
 * @throws kotlinx.coroutines.TimeoutCancellationException when [action] runs longer than [timeout].
 */
fun <T> timeoutRunBlocking(
  timeout: Duration = 10.seconds,
  context: CoroutineContext = EmptyCoroutineContext,
  action: suspend CoroutineScope.() -> T,
): T {
  @Suppress("RAW_RUN_BLOCKING")
  return runBlocking(context) { withTimeout(timeout, action) }
}
