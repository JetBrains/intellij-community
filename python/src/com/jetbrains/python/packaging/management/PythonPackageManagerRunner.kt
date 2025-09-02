// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal object PythonPackageManagerRunner {
  suspend fun runProcess(
    process: Process,
    command: String,
  ): ProcessOutput {
    val handler = PyPackageProcessHandler(process, command)

    return runProcessInternal(handler)
  }

  private suspend fun runProcessInternal(processHandler: PyPackageProcessHandler): ProcessOutput = withContext(Dispatchers.IO) {
    coroutineToIndicator {
      val progressIndicator = ProgressManager.getInstance().progressIndicator
      processHandler.lastLineNotifier = { line: String ->
        @Suppress("HardCodedStringLiteral")
        progressIndicator.text = line.trim()
      }
      processHandler.runProcessWithProgressIndicator(progressIndicator, 10 * 60 * 1000)
    }
  }
}