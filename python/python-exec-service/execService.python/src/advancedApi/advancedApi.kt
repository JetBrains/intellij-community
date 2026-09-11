// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.python.advancedApi

import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.FileReporter
import com.intellij.python.community.execService.HowToReportFile
import com.intellij.python.community.execService.ProcessInteractiveHandler
import com.intellij.python.community.execService.ProcessOutputTransformer
import com.intellij.python.community.execService.PyProcessListener
import com.intellij.python.community.execService.impl.transformerToHandler
import com.intellij.python.community.execService.python.PyHelper
import com.intellij.python.community.execService.python.StdInProvider
import com.intellij.python.community.execService.python.impl.asChannelConsumer
import com.intellij.python.community.execService.reportOutputAsProgress
import com.intellij.python.community.helpersLocator.PythonHelpersLocator
import com.jetbrains.python.PYTHONPATH
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.impl.PY3_HELPER_DEPENDENCIES_DIR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// This in advanced API, most probably you need "api.kt"

/**
 * Execute [python]
 */
suspend fun <T> ExecService.executePythonAdvanced(
  python: ExecutablePython,
  args: Args,
  options: ExecOptions = ExecOptions(),
  processInteractiveHandler: ProcessInteractiveHandler<T>,
): PyResult<T> =
  executeAdvanced(
    binary = python.binary,
    args = Args(*python.args.toTypedArray()).add(args),
    // TODO: Merge PATH
    options = options.copy(env = options.env + python.env), processInteractiveHandler)


/**
 * Execute [helper] on [python]. For remote eels, [helper] is copied (but only one file!).
 * To write something into the `stdin` of [helper], use [stdInProvider].
 * The process output is reported as progress.
 */
suspend fun <T> ExecService.executeHelperAdvanced(
  python: ExecutablePython,
  helper: PyHelper,
  args: Args = Args(),
  options: ExecOptions = ExecOptions(),
  procListener: PyProcessListener? = null,
  stdInProvider: StdInProvider? = null,
  processOutputTransformer: ProcessOutputTransformer<T>,
): PyResult<T> = reportOutputAsProgress(procListener) { listener ->
  executePythonAdvanced(
    python,
    Args().addHelper(helper).add(args),
    options,
    transformerToHandler(listener, stdInProvider?.asChannelConsumer(), processOutputTransformer))
}

/**
 * Adds helper by copying it to the remote system (if needed)
 */
private suspend fun Args.addHelper(helper: PyHelper): Args =
  withContext(Dispatchers.IO) {
    if (helper.addDependency) {
      // Helper needs a dependency
      val additionalDir = PythonHelpersLocator.findPathInHelpers(PY3_HELPER_DEPENDENCIES_DIR)
      addLocalFile(additionalDir, FileReporter { it to HowToReportFile.EnvVar(PYTHONPATH) })
    }

    val helper = PythonHelpersLocator.findPathInHelpers(helper.name)
    addLocalFile(helper)
    this@addHelper
  }
