// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import com.intellij.execution.process.ProcessOutput
import com.intellij.platform.eel.provider.asEelPath
import com.jetbrains.python.errorProcessing.ExecError
import com.jetbrains.python.errorProcessing.ExecErrorReason
import com.jetbrains.python.errorProcessing.MessageError
import java.io.IOException
import kotlin.io.path.Path

/**
 * A temporary hack for some outdated code (see usages), do not use in a new code. Stay away from [PyExecutionException]
 */
internal fun PyExecutionException.copyWith(newCommand: String, newArgs: List<String>): PyExecutionException =
  when (val err = pyError) {
    is ExecError -> {
      when (val reason = err.errorReason) {
        is ExecErrorReason.CantStart -> {
          PyExecutionException(IOException(reason.cantExecProcessError), err.additionalMessageToUser, newCommand, newArgs, fixes)
        }
        ExecErrorReason.Timeout -> {
          PyExecutionException(ExecError(Path(newCommand).asEelPath(), newArgs.toTypedArray(), ExecErrorReason.Timeout, err.additionalMessageToUser))
        }
        is ExecErrorReason.UnexpectedProcessTermination -> {
          val output = ProcessOutput(reason.stdout, reason.stderr, reason.exitCode, false, false)
          PyExecutionException(err.additionalMessageToUser, newCommand, newArgs, output, fixes)
        }
      }
    }
    is MessageError -> error("Error ${err.message} has no command, command can't be changed")
  }