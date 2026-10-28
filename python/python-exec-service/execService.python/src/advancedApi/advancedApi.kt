// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.python.advancedApi

import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.EnvVarVal
import com.intellij.python.community.execService.ExecGetProcessOptions
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.ExecuteGetProcessError
import com.intellij.python.community.execService.HowToReportFile.AnArgument
import com.intellij.python.community.execService.HowToReportFile.AsEnvVar
import com.intellij.python.community.execService.ProcessInteractiveHandler
import com.intellij.python.community.execService.ProcessOutputTransformer
import com.intellij.python.community.execService.PyProcessListener
import com.intellij.python.community.execService.RelativePath
import com.intellij.python.community.execService.impl.transformerToHandler
import com.intellij.python.community.execService.python.PyHelper
import com.intellij.python.community.execService.python.StdInProvider
import com.intellij.python.community.execService.python.impl.asChannelConsumer
import com.intellij.python.community.execService.reportOutputAsProgress
import com.intellij.python.community.helpersLocator.PythonHelpersLocator
import com.jetbrains.python.PYTHONIOENCODING
import com.jetbrains.python.PYTHONPATH
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.impl.PY3_HELPER_DEPENDENCIES_DIR
import kotlinx.coroutines.CoroutineScope
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
    options = options.copy(env = options.env + python.env), processInteractiveHandler)


/**
 * Execute [helper] on [python]. For a remote eel, we copy the full helpers directory, because helpers can import other helpers.
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
    // We read the output as UTF-8, but Python on Windows writes it in the ANSI code page by default.
    options.copy(env = CHARSET_ENV + options.env),
    transformerToHandler(listener, stdInProvider?.asChannelConsumer(), processOutputTransformer))
}

/**
 * Starts [helper] on [python] and returns the process. The caller owns the process, see [ExecService.executeGetProcess].
 * Use it for a helper that serves for a long time, such as a debug adapter. A helper that runs to completion belongs to
 * [executeHelperAdvanced], which also reports its output and applies a timeout.
 */
suspend fun ExecService.executeHelperGetProcess(
  python: ExecutablePython,
  helper: PyHelper,
  args: Args = Args(),
  scopeToBind: CoroutineScope? = null,
  options: ExecGetProcessOptions = ExecGetProcessOptions(),
): Result<Process, ExecuteGetProcessError<*>> =
  executeGetProcess(
    binary = python.binary,
    args = Args(*python.args.toTypedArray()).addHelper(helper).add(args),
    scopeToBind = scopeToBind,
    // We read the process output as UTF-8, but Python on Windows writes it in the ANSI code page by default.
    options = options.copy(env = buildList {
      addAll(CHARSET_ENV)
      addAll(options.env)
      addAll(python.env)
    }),
  )

/**
 * Adds [helper] as an argument, and the helper dependencies directory to `PYTHONPATH`.
 * The full helpers directory is copied to the remote system if necessary.
 */
private suspend fun Args.addHelper(helper: PyHelper): Args =
  withContext(Dispatchers.IO) {
    addLocalDir(PythonHelpersLocator.getCommunityHelpersRoot()) {
      findChild(helper, andReport = { it to AnArgument })
      findChild(RelativePath(PY3_HELPER_DEPENDENCIES_DIR), andReport = { it to AsEnvVar(PYTHONPATH) })
    }
  }


private val CHARSET_ENV =
  listOf(com.intellij.python.community.execService.EnvVar(PYTHONIOENCODING, EnvVarVal.BareStr(Charsets.UTF_8.name())))