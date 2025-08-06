// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.python

import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.PyProcessListener
import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.intellij.python.community.execService.python.advancedApi.ExecutablePython
import com.intellij.python.community.execService.python.advancedApi.executeHelperAdvanced
import com.intellij.python.community.execService.python.advancedApi.validatePythonAndGetVersion
import com.intellij.python.community.helpersLocator.PythonHelpersLocator
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.psi.LanguageLevel
import org.jetbrains.annotations.ApiStatus

/**
 * Execute [helper] on [python]. For remote eels, [helper] is copied (but only one file!).
 * Returns `stdout`
 */
suspend fun ExecService.executeHelper(
  python: PythonBinary,
  helper: HelperName,
  args: List<String> = emptyList(),
  options: ExecOptions = ExecOptions(),
  procListener: PyProcessListener? = null,
): PyResult<String> =
  executeHelperAdvanced(ExecutablePython.vanillaExecutablePython(python), helper, args, options, procListener, ZeroCodeStdoutTransformer)

/**
 * Ensures that this python is executable and returns its version. Error if python is broken.
 *
 * Some pythons might be broken: they may be executable, even return a version, but still fail to execute it.
 * As we need workable pythons, we validate it by executing
 */
@ApiStatus.Internal
suspend fun ExecService.validatePythonAndGetVersion(python: PythonBinary): PyResult<LanguageLevel> =
  validatePythonAndGetVersion(ExecutablePython.vanillaExecutablePython(python))

suspend fun PythonBinary.validatePythonAndGetVersion(): PyResult<LanguageLevel> = ExecService().validatePythonAndGetVersion(this)


/**
 * Adds helper by copying it to the remote system (if needed)
 */
fun Args.addHelper(helper: HelperName): Args =
  addLocalFile(PythonHelpersLocator.findPathInHelpers(helper))