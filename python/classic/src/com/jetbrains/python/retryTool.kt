// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.openapi.diagnostic.Logger
import com.jetbrains.python.errorProcessing.PyResult
import kotlinx.coroutines.delay
import org.jetbrains.annotations.NonNls
import kotlin.time.Duration.Companion.seconds

private const val RETRY = 3
private val DELAY = 2.seconds

/**
 * Runs [code], which executes a helper described by [debugString]. A remote server can fail for a short time,
 * so after a failure we wait [DELAY] and run [code] again, up to [RETRY] more times.
 * If the last run also fails, we throw [IllegalStateException]: the helper itself is broken.
 */
@Throws(IllegalStateException::class)
internal suspend fun <T : Any> pyRetryCommand(log: Logger, debugString: () -> @NonNls String, code: suspend () -> PyResult<T>): T {
  repeat(RETRY) {
    when (val r = code()) {
      is Result.Failure -> {
        log.warn("Error running ${debugString()} : ${r.error}")
        delay(DELAY) // If the project or the application closes now, `delay` throws CancellationException
      }
      is Result.Success -> return r.result
    }
  }
  // `message` of an ExecError has the command, `stdout`, `stderr` and the exit code, which are necessary to fix the helper.
  return code().orThrow { err -> error("Error running ${debugString()} : ${err.message}") }
}