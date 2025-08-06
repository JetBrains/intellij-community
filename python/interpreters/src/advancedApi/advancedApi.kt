// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.interpreters.advancedApi

import com.intellij.python.community.execService.*
import com.intellij.python.community.execService.python.HelperName
import com.intellij.python.community.execService.python.advancedApi.executeHelperAdvanced
import com.intellij.python.community.execService.python.advancedApi.executePythonAdvanced
import com.intellij.python.community.execService.python.advancedApi.validatePythonAndGetVersion
import com.intellij.python.community.interpreters.ValidInterpreter
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.psi.LanguageLevel
import org.jetbrains.annotations.ApiStatus

// This in advanced API, most probably you need "api.kt"

/**
 * Execute [python]
 */
suspend fun <T> ExecService.executePythonAdvanced(
  python: ValidInterpreter,
  args: Args,
  options: ExecOptions = ExecOptions(),
  processInteractiveHandler: ProcessInteractiveHandler<T>,
): PyResult<T> =
  executePythonAdvanced(python.asExecutablePython, args, options, processInteractiveHandler)


/**
 * Execute [helper] on [python]. For remote eels, [helper] is copied (but only one file!).
 */
suspend fun <T> ExecService.executeHelperAdvanced(
  python: ValidInterpreter,
  helper: HelperName,
  args: List<String> = emptyList(),
  options: ExecOptions = ExecOptions(),
  procListener: PyProcessListener? = null,
  processOutputTransformer: ProcessOutputTransformer<T>,
): PyResult<T> = executeHelperAdvanced(python.asExecutablePython, helper, args, options, procListener, processOutputTransformer)

/**
 * Ensures that this python is executable and returns its version. Error if python is broken.
 *
 * Some pythons might be broken: they may be executable, even return a version, but still fail to execute it.
 * As we need workable pythons, we validate it by executing
 */
@ApiStatus.Internal
suspend fun ValidInterpreter.validatePythonAndGetVersion(): PyResult<LanguageLevel> =
  ExecService().validatePythonAndGetVersion(asExecutablePython)

/**
 * Execute [helper] on [python]. For remote eels, [helper] is copied (but only one file!).
 * Returns `stdout`
 */
suspend fun ExecService.executeHelper(
  python: ValidInterpreter,
  helper: HelperName,
  args: List<String> = emptyList(),
  options: ExecOptions = ExecOptions(),
  procListener: PyProcessListener? = null,
): PyResult<String> =
  executeHelperAdvanced(python.asExecutablePython, helper, args, options, procListener, ZeroCodeStdoutTransformer)
