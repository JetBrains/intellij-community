package com.intellij.ide.starter.utils

import com.intellij.tools.ide.util.common.NoRetryException
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Copy-pasted from
 */

/** @return T - if successful; null - otherwise */
private suspend fun <T> withRetry(messageOnFailure: String,
                          retries: Long = 3,
                          delay: Duration = 10.seconds,
                          rollback: suspend () -> Unit = {},
                          retryAction: suspend () -> T): T? {

  (1..retries).forEach { failureCount ->
    try {
      return retryAction()
    }
    catch (e: NoRetryException) {
      throw e
    }
    catch (t: Throwable) {
      rollback()
      if (failureCount == retries) {
        if (messageOnFailure.isNotBlank()) logError(messageOnFailure)
        logError("Last failure:")
        t.printStackTrace()
      }

      if (failureCount < retries) {
        logOutput("Retrying in ${delay} ...")
        delay(delay)
      }
    }
  }

  return null
}

/** @return T - if successful; null - otherwise */
@Suppress("RAW_RUN_BLOCKING")
fun <T> withRetryBlocking(
  messageOnFailure: String,
  retries: Long = 3,
  delay: Duration = 10.seconds,
  rollback: suspend () -> Unit = {},
  retryAction: suspend () -> T
): T? = runBlocking(Dispatchers.IO) {
  withRetry(
    messageOnFailure = messageOnFailure,
    retries = retries,
    delay = delay,
    rollback = rollback
  ) { retryAction() }
}