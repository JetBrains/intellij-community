// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.execution.process.CapturingAnsiEscapesAwareProcessHandler
import com.intellij.execution.process.CapturingProcessAdapter
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.util.Key

internal class PyPackageProcessHandler : CapturingAnsiEscapesAwareProcessHandler {
  constructor(process: Process, commandLine: String) : super(process, commandLine)

  var lastLine: String = ""
    private set

  var lastLineNotifier: ((String) -> Unit)? = null

  override fun createProcessAdapter(processOutput: ProcessOutput): CapturingProcessAdapter? {
    return PackageProcessAdapter(processOutput)
  }

  private inner class PackageProcessAdapter(processOutput: ProcessOutput) : AnsiEscapesAwareAdapter(processOutput) {
    override fun addToOutput(text: String, outputType: Key<*>) {
      super.addToOutput(text, outputType)

      when {
        outputType === ProcessOutputTypes.STDOUT -> {
          lastLine = output.stdoutLines.lastOrNull { it.isNotBlank() } ?: ""

        }
        outputType === ProcessOutputTypes.STDERR -> {
          lastLine = output.stderrLines.lastOrNull { it.isNotBlank() } ?: ""
        }
      }
      lastLine = lastLine.trim()
      if (lastLine.isBlank())
        return
      lastLineNotifier?.invoke(lastLine)
    }
  }
}