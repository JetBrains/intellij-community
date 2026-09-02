// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.platform.eel.provider.utils.stderrString
import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.python.processOutput.common.ExecErrorDto
import com.intellij.python.processOutput.common.ExecErrorReasonDto
import com.intellij.python.processOutput.common.sendExecErrorEvent
import com.jetbrains.python.errorProcessing.ExecError
import com.jetbrains.python.errorProcessing.ExecErrorReason
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun showProcessExecutionErrorDialog(execError: ExecError) {
  sendExecErrorEvent(
    execErrorDto = ExecErrorDto(
      message = execError.message,
      command = execError.asCommand,
      reason =
        when (val reason = execError.errorReason) {
          is ExecErrorReason.CantStart ->
            ExecErrorReasonDto.CantStart(
              cantExecProcessError = reason.cantExecProcessError
            )
          is ExecErrorReason.UnexpectedProcessTermination ->
            ExecErrorReasonDto.UnexpectedTermination(
              stdout = reason.stdoutString,
              stderr = reason.stderrString,
              exitCode = reason.exitCode
            )
          ExecErrorReason.Timeout ->
            ExecErrorReasonDto.Timeout
        },
      loggedProcessId = execError.loggedProcessId,
      additionalMessageToUser = execError.additionalMessageToUser
    )
  )
}
