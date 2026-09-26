// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.BinaryToExec
import com.intellij.python.community.execService.ExecGetProcessOptions
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.ExecuteGetProcessError
import com.intellij.python.community.execService.ProcessOutputTransformer
import com.intellij.python.community.execService.PyProcessListener
import com.intellij.python.community.execService.python.PyHelper
import com.intellij.python.community.execService.python.StdInProvider
import com.intellij.python.sdk.backend.PythonInterpreter
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.impl.asBinToExecuteImpl
import com.jetbrains.python.sdk.impl.execGetStdoutImpl
import com.jetbrains.python.sdk.impl.executeGetProcessImpl
import com.jetbrains.python.sdk.impl.executeHelperImpl
import com.jetbrains.python.sdk.impl.executeImpl
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.CheckReturnValue

// Various functions to execute code against SDK
//
// Every function below takes a [PythonInterpreter], which names the SDK to run. See [asBinToExecute] for what the
// interpreter contributes, and for what it does not.


// See function it calls for more info
@Internal
@CheckReturnValue
suspend fun ExecService.execGetStdout(
  interpreter: PythonInterpreter,
  args: Args,
  options: ExecOptions = ExecOptions(),
  procListener: PyProcessListener? = null,
): PyResult<String> = execGetStdoutImpl(interpreter.sdk, args, options, procListener)

// See function it calls for more info
@Internal
@CheckReturnValue
suspend fun <T> ExecService.execute(
  interpreter: PythonInterpreter,
  args: Args = Args(),
  options: ExecOptions = ExecOptions(),
  procListener: PyProcessListener? = null,
  processOutputTransformer: ProcessOutputTransformer<T>,
): PyResult<T> = executeImpl(interpreter.sdk, args, options, procListener, processOutputTransformer)


/**
 * Executes [helper] on [interpreter] (copies it to the remote machine if needed)
 * To write something into the `stdin` of [helper], use [stdInProvider].
 */
@Internal
@CheckReturnValue
suspend fun ExecService.executeHelper(
  interpreter: PythonInterpreter,
  helper: PyHelper,
  helperArgs: Args = Args(),
  options: ExecOptions = ExecOptions(),
  procListener: PyProcessListener? = null,
  stdInProvider: StdInProvider? = null,
): PyResult<String> = executeHelperImpl(interpreter.sdk, helper, helperArgs, options, procListener, stdInProvider)


// See function it calls for more info
@Internal
@CheckReturnValue
suspend fun ExecService.executeGetProcess(
  interpreter: PythonInterpreter,
  args: Args = Args(),
  scopeToBind: CoroutineScope? = null,
  options: ExecGetProcessOptions = ExecGetProcessOptions(),
): Result<Process, ExecuteGetProcessError<*>> = executeGetProcessImpl(interpreter.sdk, args, scopeToBind, options)


/**
 * Converts SDK to [com.intellij.python.community.execService.BinOnTarget] to be used by [com.intellij.python.community.execService.ExecService]
 *
 * This reads the SDK's own home path, or its target data. The detection result of
 * [com.intellij.python.sdk.backend.PythonInterpreter] plays no part here. A failed detection does not stop the
 * execution. Ask [com.intellij.python.sdk.backend.getPythonInfo] first when the caller must know that the interpreter
 * works.
 */
@Internal
fun PythonInterpreter.asBinToExecute(): Result<BinaryToExec, MessageError> = sdk.asBinToExecuteImpl()

/**
 * Configures [targetCommandLineBuilder] (sets a binary path and other stuff) so it could run python on this target
 */
@Internal
fun Sdk.configureBuilderToRunPythonOnTarget(targetCommandLineBuilder: TargetedCommandLineBuilder) {
  pySdkAdditionalData.flavorAndData.data.prepareTargetCommandLine(this, targetCommandLineBuilder)
}
