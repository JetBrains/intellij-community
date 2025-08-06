// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.errorProcessing

import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.utils.EelProcessExecutionResult
import com.intellij.platform.eel.provider.utils.EelProcessExecutionResultInfo
import com.intellij.platform.eel.provider.utils.stderrString
import com.intellij.platform.eel.provider.utils.stdoutString
import com.jetbrains.python.PyCommunityBundle
import org.jetbrains.annotations.Nls
import kotlin.io.path.Path

/**
 * Exe might sit on eel (new one) or on target (legacy)
 */
sealed interface Exe {
  companion object {
    fun fromString(path: String): Exe {
      try {
        return OnEel(Path(path).asEelPath())
      }
      catch (_: EelPathException) {
        return OnTarget(path)
      }
    }
  }

  data class OnEel(val eelPath: EelPath) : Exe {
    override fun toString(): String = eelPath.toString()
  }

  data class OnTarget(val path: String) : Exe {
    override fun toString(): String = path
  }
}

/**
 * External process error.
 */
class ExecError(
  val exe: Exe,
  /**
   * I.e ['-v']
   */
  val args: Array<out String>,

  val errorReason: ExecErrorReason,
  /**
   * optional message to be displayed to the user: Why did we run this process. I.e "running pip to install package".
   */
  val additionalMessageToUser: @NlsContexts.DialogTitle String? = null,
) : PyError(getExecErrorMessage(exe.toString(), args, additionalMessageToUser, errorReason)) {
  val asCommand: String get() = (arrayOf(exe.toString()) + args).joinToString(" ")
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
   * [processOutput] contains exit code, stdout etc
   */
  data class UnexpectedProcessTermination(private val processOutput: EelProcessExecutionResult) : ExecErrorReason, EelProcessExecutionResultInfo by processOutput

  /**
   * Process started, but killed due to timeout without returning any useful data
   */
  data object Timeout : ExecErrorReason
}

fun ProcessOutput.asExecutionFailed(): ExecErrorReason.UnexpectedProcessTermination =
  ExecErrorReason.UnexpectedProcessTermination(EelProcessExecutionResult(exitCode, stdout.encodeToByteArray(), stderr.encodeToByteArray()))

private fun getExecErrorMessage(
  exec: String,
  args: Array<out String>,
  additionalMessage: @NlsContexts.DialogTitle String?,
  execErrorReason: ExecErrorReason,
): @Nls String {
  val commandLine = exec + " " + args.joinToString(" ")
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
                                r.stdoutString,
                                r.stderrString,
                                r.exitCode)
    }

    ExecErrorReason.Timeout -> {
      PyCommunityBundle.message("python.execution.timeout",
                                additionalMessage ?: "",
                                commandLine)
    }
  }
}


