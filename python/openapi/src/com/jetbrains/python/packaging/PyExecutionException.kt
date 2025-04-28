// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.util.NlsContexts
import com.jetbrains.python.errorProcessing.*
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

/**
 * Wraps [PyError] for cases where [ExecutionException] is used.
 * As [PyResult] should be used instead of exceptions, new code should use [PyError]
 *
 * Migrate to [PyError], please.
 */
@ApiStatus.Obsolete
class PyExecutionException private constructor(
  val pyError: PyError,
  val fixes: List<PyExecutionFix>,
  ioException: IOException? = null,
) : ExecutionException(pyError.message, ioException) {
  companion object {
    /**
     * [com.jetbrains.python.packaging.PyExecutionException] for process died with timeout
     */
    @JvmStatic
    fun createForTimeout(
      additionalMessageToUser: @NlsContexts.DialogMessage String?,
      command: String,
      args: List<String>,
    ): PyExecutionException = PyExecutionException(ExecError(arrayOf(command) + args.toTypedArray(), ExecErrorReason.Timeout, additionalMessageToUser))
  }


  @JvmOverloads
  constructor(
    pyError: PyError,
    fixes: List<PyExecutionFix> = listOf<PyExecutionFix>(),
  ) : this(pyError, fixes, null)

  /**
   * System decided not to start a process at all due to [messageToUser]
   */
  @JvmOverloads
  constructor(
    messageToUser: @NlsContexts.DialogMessage String,
    fixes: List<PyExecutionFix> = listOf<PyExecutionFix>(),
  ) : this(
    pyError = MessageError(messageToUser),
    fixes = fixes)

  /**
   * A process failed to start, [ExecErrorReason.CantStart]
   *
   * @param additionalMessage a process start reason for a user
   */
  @JvmOverloads
  constructor(
    startException: IOException,
    additionalMessage: @NlsContexts.DialogMessage String?,
    command: String,
    args: List<String>,
    fixes: List<PyExecutionFix> = listOf<PyExecutionFix>(),
  ) : this(
    pyError = ExecError(arrayOf(command) + args.toTypedArray(), ExecErrorReason.CantStart(null, startException.localizedMessage), additionalMessage),
    fixes = fixes,
    ioException = startException)


  /**
   * A process started, but failed [com.jetbrains.python.errorProcessing.ExecErrorReason.UnexpectedProcessTermination]
   *
   * @param additionalMessage a process start reason for a user
   * @param output            execution output
   */
  @JvmOverloads
  constructor(
    additionalMessage: @NlsContexts.DialogMessage String?,
    command: String,
    args: List<String>,
    output: ProcessOutput,
    fixes: List<PyExecutionFix> = listOf<PyExecutionFix>(),
  ) : this(
    pyError = ExecError(arrayOf(command) + args.toTypedArray(), output.asExecutionFailed()),
    fixes = fixes)

  /**
   * A process started, but failed [ExecErrorReason.UnexpectedProcessTermination]
   *
   * @param additionalMessage a process start reason for a user
   */
  constructor(
    additionalMessage: @NlsContexts.DialogMessage String?,
    command: String,
    args: List<String>,
    stdout: String,
    stderr: String,
    exitCode: Int,
    fixes: List<PyExecutionFix>,
  ) : this(additionalMessage, command, args, ProcessOutput(stdout, stderr, exitCode, false, false), fixes)
}