// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.execService.*
import com.intellij.python.community.execService.python.HelperName
import com.intellij.python.community.execService.python.addHelper
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CheckReturnValue
import java.nio.file.Path

// Various functions to execute code against SDK

// See function it calls for more info
@ApiStatus.Internal
@CheckReturnValue
suspend fun ExecService.execGetStdout(
  sdk: Sdk,
  args: Args,
  options: ExecOptions = ExecOptions(),
  procListener: PyProcessListener? = null,
): PyResult<String> = execGetStdout(sdk.asBinToExecute(), args, options, procListener)

// See function it calls for more info
@ApiStatus.Internal
@CheckReturnValue
suspend fun <T> ExecService.execute(
  sdk: Sdk,
  args: Args = Args(),
  options: ExecOptions = ExecOptions(),
  procListener: PyProcessListener? = null,
  processOutputTransformer: ProcessOutputTransformer<T>,
): PyResult<T> = execute(sdk.asBinToExecute(), args, options, procListener, processOutputTransformer)


/**
 * Executes [helper] on [sdk] (copies it to the remote machine if needed)
 */
@ApiStatus.Internal
@CheckReturnValue
suspend fun ExecService.executeHelper(
  sdk: Sdk,
  helper: HelperName,
  helperArgs: List<String> = emptyList(),
  options: ExecOptions = ExecOptions(),
  procListener: PyProcessListener? = null,
): PyResult<String> = executeHelper(sdk, helper, Args(*helperArgs.toTypedArray()), options, procListener)

/**
 * Executes [helper] on [sdk] (copies it to the remote machine if needed)
 */
@ApiStatus.Internal
@CheckReturnValue
suspend fun ExecService.executeHelper(
  sdk: Sdk,
  helper: HelperName,
  helperArgs: Args = Args(),
  options: ExecOptions = ExecOptions(),
  procListener: PyProcessListener? = null,
): PyResult<String> = execGetStdout(sdk, Args().addHelper(helper).add(helperArgs), options, procListener)


// See function it calls for more info
@ApiStatus.Internal
@CheckReturnValue
suspend fun ExecService.executeGetProcess(
  sdk: Sdk,
  args: Args = Args(),
  scopeToBind: CoroutineScope? = null,
  options: ExecGetProcessOptions = ExecGetProcessOptions(),
): Result<Process, ExecuteGetProcessError<*>> = executeGetProcess(sdk.asBinToExecute(), args, scopeToBind, options)

/**
 * Converts SDK to [BinOnTarget] to be used by [ExecService]
 */
@ApiStatus.Internal
fun Sdk.asBinToExecute(): BinaryToExec = when (val additionalData = getOrCreateAdditionalData()) {
  is PyTargetAwareAdditionalData -> BinOnTarget(
    configureTargetCmdLine = this::configureBuilderToRunPythonOnTarget,
    target = additionalData.targetEnvironmentConfiguration ?: error("Target is not configured"),
  )
  else -> BinOnEel(Path.of(homePath ?: error("Home path is not set")))
}
