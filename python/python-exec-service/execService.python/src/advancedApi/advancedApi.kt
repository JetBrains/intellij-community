// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.python.advancedApi

import com.intellij.python.community.execService.*
import com.intellij.python.community.execService.impl.transformerToHandler
import com.intellij.python.community.execService.python.HelperName
import com.intellij.python.community.execService.python.addHelper
import com.intellij.python.community.execService.python.impl.validatePythonAndGetVersionImpl
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.psi.LanguageLevel
import org.jetbrains.annotations.ApiStatus

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
    binary = BinOnEel(python.binary),
    args = Args(*python.args.toTypedArray()).add(args),
    // TODO: Merge PATH
    options = options.copy(env = options.env + python.env), processInteractiveHandler)


/**
 * Execute [helper] on [python]. For remote eels, [helper] is copied (but only one file!).
 */
suspend fun <T> ExecService.executeHelperAdvanced(
  python: ExecutablePython,
  helper: HelperName,
  args: List<String> = emptyList(),
  options: ExecOptions = ExecOptions(),
  procListener: PyProcessListener? = null,
  processOutputTransformer: ProcessOutputTransformer<T>,
): PyResult<T> = executePythonAdvanced(
  python,
  Args().addHelper(helper).addArgs(args),
  options, transformerToHandler(procListener, processOutputTransformer))

/**
 * Ensures that this python is executable and returns its version. Error if python is broken.
 *
 * Some pythons might be broken: they may be executable, even return a version, but still fail to execute it.
 * As we need workable pythons, we validate it by executing
 */
@ApiStatus.Internal
suspend fun ExecService.validatePythonAndGetVersion(python: ExecutablePython): PyResult<LanguageLevel> =
  validatePythonAndGetVersionImpl(python)
