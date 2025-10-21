// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.python

import com.intellij.python.community.execService.*
import com.intellij.python.community.execService.python.advancedApi.ExecutablePython
import com.intellij.python.community.execService.python.advancedApi.executeHelperAdvanced
import com.intellij.python.community.execService.python.advancedApi.validatePythonAndGetInfo
import com.intellij.python.community.helpersLocator.PythonHelpersLocator
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonInfo
import com.jetbrains.python.errorProcessing.PyResult

/**
 * Python binary itself (i.e python.exe)
 */
typealias PythonBinaryOnEelOrTarget = BinaryToExec


/**
 * Execute [helper] on [python]. For remote eels, [helper] is copied (but only one file!).
 * Returns `stdout`
 */
suspend fun ExecService.executeHelper(
  python: BinaryToExec,
  helper: HelperName,
  args: List<String> = emptyList(),
  options: ExecOptions = ExecOptions(),
  procListener: PyProcessListener? = null,
): PyResult<String> =
  executeHelperAdvanced(ExecutablePython.vanillaExecutablePython(python), helper, args, options, procListener, ZeroCodeStdoutTransformer)

/**
 * Ensures that this python is executable and returns its info. Error if python is broken.
 *
 * Some pythons might be broken: they may be executable, even return a version, but still fail to execute it.
 * As we need workable pythons, we validate it by executing
 */
suspend fun ExecService.validatePythonAndGetInfo(python: PythonBinaryOnEelOrTarget): PyResult<PythonInfo> =
  validatePythonAndGetInfo(ExecutablePython.vanillaExecutablePython(python))

suspend fun PythonBinaryOnEelOrTarget.validatePythonAndGetInfo(): PyResult<PythonInfo> = ExecService().validatePythonAndGetInfo(this)
suspend fun ExecService.validatePythonAndGetInfo(python: PythonBinary): PyResult<PythonInfo> = validatePythonAndGetInfo(python.asBinToExec())
suspend fun PythonBinary.validatePythonAndGetInfo(): PyResult<PythonInfo> = asBinToExec().validatePythonAndGetInfo()


/**
 * Adds helper by copying it to the remote system (if needed)
 */
fun Args.addHelper(helper: HelperName): Args =
  addLocalFile(PythonHelpersLocator.findPathInHelpers(helper))