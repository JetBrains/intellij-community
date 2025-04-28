// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.errorProcessing

import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.util.NlsContexts
import com.jetbrains.python.PyCommunityBundle
import org.jetbrains.annotations.Nls

/**
 * External process error.
 */
class ExecError(
  /**
   * I.e ['python', '-v']
   */
  val command: Array<String>,

  val errorReason: ExecErrorReason,
  /**
   * optional message to be displayed to the user: Why did we run this process. I.e "running pip to install package".
   */
  val additionalMessageToUser: @NlsContexts.DialogTitle String? = null,
) : PyError(getExecErrorMessage(command, additionalMessageToUser, errorReason)) {
  val exeAndArgs: Pair<String, Array<String>> = Pair(command[0], command.drop(1).toTypedArray())

  init {
    assert(command.isNotEmpty()) { "Command can't be empty" }
  }
}


sealed interface ExecErrorReason {
  /**
   * A process failed to start.
   * That means the process hasn't been even created.
   * Os might return [errNo] (not always possible to fetchit) and [cantExecProcessError]
   */
  data class CantStart(val errNo: Int?, val cantExecProcessError: String) : ExecErrorReason

  /**
   * A process started but failed with an error.
   */
  data class UnexpectedProcessTermination(val exitCode: Int, val stdout: String, val stderr: String) : ExecErrorReason

  /**
   * Process started, but killed due to timeout without returning any useful data
   */
  data object Timeout : ExecErrorReason
}

fun ProcessOutput.asExecutionFailed(): ExecErrorReason.UnexpectedProcessTermination =
  ExecErrorReason.UnexpectedProcessTermination(exitCode, stdout, stderr)

internal fun getExecErrorMessage(
  command: Array<String>,
  additionalMessage: @NlsContexts.DialogTitle String?,
  execErrorReason: ExecErrorReason,
): @Nls String {
  val commandLine = command.joinToString(" ")
  return when (val r = execErrorReason) {
    is ExecErrorReason.CantStart -> {
      PyCommunityBundle.message("python.execution.cant.start.error",
                                additionalMessage ?: "",
                                commandLine,
                                r.cantExecProcessError,
                                r.errNo ?: "unknown")
    }
    is ExecErrorReason.UnexpectedProcessTermination -> {
      PyCommunityBundle.message("python.execution.error",
                                additionalMessage ?: "",
                                commandLine,
                                r.stdout,
                                r.stderr,
                                r.exitCode)
    }

    ExecErrorReason.Timeout -> {
      PyCommunityBundle.message("python.execution.timeout",
                                additionalMessage ?: "",
                                commandLine)
    }
  }
}


