// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.python

import com.intellij.openapi.util.NlsSafe
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.BinaryToExec
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.PyProcessListener
import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.intellij.python.community.execService.python.advancedApi.ExecutablePython
import com.intellij.python.community.execService.python.advancedApi.executeHelperAdvanced
import com.intellij.python.community.execService.python.impl.execGetStdoutBoolImpl
import com.intellij.python.community.execService.python.impl.execGetStdoutImpl
import com.intellij.python.community.execService.python.impl.validatePythonAndGetInfoImpl
import com.intellij.python.community.helpersLocator.PythonHelpersLocator
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
 */
@Deprecated("Currently used only for targets, for eel paths use PythonBinary.detectPythonEnvironment()", ReplaceWith(
    "PythonBinary.detectPythonEnvironment().mapResult { it.getPythonInfo() }"
))
suspend fun PythonBinaryOnEelOrTarget.validatePythonAndGetInfo(): PyResult<PythonInfo> {
  return ExecService().validatePythonAndGetInfoImpl(ExecutablePython.vanillaExecutablePython(this))
}

/**
 * Execute [pythonCode] on [ExecutablePython] and (if exitcode is 0) return stdout
 */
suspend fun ExecutablePython.execGetStdout(pythonCode: @NlsSafe String, execService: ExecService = ExecService()): PyResult<String> = execService.execGetStdoutImpl(this, pythonCode, ZeroCodeStdoutTransformer)
suspend fun PythonBinaryOnEelOrTarget.execGetStdout(pythonCode: @NlsSafe String, execService: ExecService = ExecService()): PyResult<String> = execService.execGetStdoutImpl(ExecutablePython.vanillaExecutablePython(this), pythonCode, ZeroCodeStdoutTransformer)

/**
 * Execute [pythonCode] on [ExecutablePython] and (if exitcode is 0) return stdout converted to [Boolean]. Useful for things like:
 * ```kotlin
 *  when (val r = executeGetBoolFromStdout("print(some_system_check()")) {
 *    is Success<*> -> {/*r is true of false*/}
 *    is Failure<*> ->  {/*r is an error here*/}
 *  }
 * ```
 */
suspend fun ExecutablePython.execGetBoolFromStdout(pythonCode: @NlsSafe String, execService: ExecService = ExecService()): PyResult<Boolean> = execService.execGetStdoutBoolImpl(this, pythonCode)
suspend fun PythonBinaryOnEelOrTarget.execGetBoolFromStdout(pythonCode: @NlsSafe String, execService: ExecService = ExecService()): PyResult<Boolean> = execService.execGetStdoutBoolImpl(ExecutablePython.vanillaExecutablePython(this), pythonCode)


/**
 * Adds helper by copying it to the remote system (if needed)
 */
fun Args.addHelper(helper: HelperName): Args =
  addLocalFile(PythonHelpersLocator.findPathInHelpers(helper))