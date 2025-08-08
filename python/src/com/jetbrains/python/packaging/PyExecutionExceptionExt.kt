// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import com.intellij.execution.process.ProcessOutput
import com.intellij.platform.eel.provider.utils.stderrString
import com.intellij.platform.eel.provider.utils.stdoutString
import com.jetbrains.python.errorProcessing.*
import java.io.IOException

/**
 * A temporary hack for some outdated code (see usages), do not use in a new code. Stay away from [PyExecutionException]
 */
internal fun PyExecutionException.copyWith(newCommand: String, newArgs: List<String>): PyExecutionException =
  when (val err = pyError) {
    is ExecError -> {
      when (val reason = err.errorReason) {
        is ExecErrorReason.CantStart -> {
          PyExecutionException(IOException(reason.cantExecProcessError), err.message, newCommand, newArgs, fixes)
        }
        ExecErrorReason.Timeout -> {
          PyExecutionException(ExecErrorImpl(Exe.fromString(newCommand), newArgs.toTypedArray(), ExecErrorReason.Timeout, err.message))
        }
        is ExecErrorReason.UnexpectedProcessTermination -> {
          val output = ProcessOutput(reason.stdoutString, reason.stderrString, reason.exitCode, false, false)
          PyExecutionException(err.message, newCommand, newArgs, output, fixes)
        }
      }
    }
    is MessageError -> error("Error ${err.message} has no command, command can't be changed")
  }