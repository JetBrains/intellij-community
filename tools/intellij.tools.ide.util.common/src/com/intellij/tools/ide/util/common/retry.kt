package com.intellij.tools.ide.util.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

enum class PrintFailuresMode {
  /** Print exceptions from each retry */
  ALL_FAILURES,

  /** Print only the very last exception from the latest retry */
  ONLY_LAST_FAILURE;
}

/** @return T - if successful; null - otherwise */
suspend fun <T> withRetry(
  messageOnFailure: String,
  retries: Long = 3,
  printFailuresMode: PrintFailuresMode = PrintFailuresMode.ALL_FAILURES,
  delay: Duration = 10.seconds,
  retryAction: suspend () -> T,
): T? {
  for (failureCount in 1..retries) {
    try {
      return retryAction()
    }
    catch (e: NoRetryException) {
      throw e
    }
    catch (t: Throwable) {
      if (messageOnFailure.isNotBlank()) {
        logError(messageOnFailure)
      }

      when (printFailuresMode) {
        PrintFailuresMode.ALL_FAILURES -> t.printStackTrace()
        PrintFailuresMode.ONLY_LAST_FAILURE -> if (failureCount == retries) {
          logError("Last failure:")
          t.printStackTrace()
        }
      }

      if (failureCount < retries) {
        logError("Retrying in ${delay} ...")
        delay(delay)
      }
    }
  }

  return null
}

/**
 * Do not retry if code fails with this exception or its inheritors
 */
open class NoRetryException(message: String, cause: Throwable?) : IllegalStateException(message, cause)

/** @return T - if successful; null - otherwise */
@Suppress("RAW_RUN_BLOCKING")
fun <T> withRetryBlocking(
  messageOnFailure: String,
  retries: Long = 3,
  printFailuresMode: PrintFailuresMode = PrintFailuresMode.ALL_FAILURES,
  delay: Duration = 10.seconds,
  retryAction: suspend () -> T
): T? = runBlocking(Dispatchers.IO) {
  withRetry(
    messageOnFailure = messageOnFailure,
    retries = retries,
    printFailuresMode = printFailuresMode,
    delay = delay
  ) { retryAction() }
}