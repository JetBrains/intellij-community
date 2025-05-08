// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.jetbrains.python.packaging.conda.PyPackageProcessHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal object PythonPackageManagerRunner {
  suspend fun runProcess(
    packageManager: PythonPackageManager,
    process: Process,
    command: String,
    @NlsContexts.ProgressTitle backgroundProgressTitle: String,
    withBackgroundProgress: Boolean,
  ): ProcessOutput {
    val handler = blockingContext {
      PyPackageProcessHandler(process, command)
    }

    val processOutput = if (withBackgroundProgress)
      withBackgroundProgress(packageManager.project, backgroundProgressTitle, true) {
        runProcessInternal(handler)
      }
    else {
      runProcessInternal(handler)
    }

    return processOutput
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