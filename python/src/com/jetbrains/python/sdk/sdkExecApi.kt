// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.BinOnTarget
import com.intellij.python.community.execService.BinaryToExec
import com.intellij.python.community.execService.ExecGetProcessOptions
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.ExecuteGetProcessError
import com.intellij.python.community.execService.ProcessOutputTransformer
import com.intellij.python.community.execService.PyProcessListener
import com.intellij.python.community.execService.execGetStdout
import com.intellij.python.community.execService.execute
import com.intellij.python.community.execService.python.HelperName
import com.intellij.python.community.execService.python.addHelper
import com.jetbrains.python.PyBundle
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CheckReturnValue
import java.nio.file.InvalidPathException
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
): PyResult<String> {
  val binToExecute = sdk.asBinToExecute().getOr { return it }
  return execGetStdout(binToExecute, args, options, procListener)
}

// See function it calls for more info
@ApiStatus.Internal
@CheckReturnValue
suspend fun <T> ExecService.execute(
  sdk: Sdk,
  args: Args = Args(),
  options: ExecOptions = ExecOptions(),
  procListener: PyProcessListener? = null,
  processOutputTransformer: ProcessOutputTransformer<T>,
): PyResult<T> {
  val binToExecute = sdk.asBinToExecute().getOr { return it }
  return execute(binToExecute, args, options, procListener, processOutputTransformer)
}


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
): Result<Process, ExecuteGetProcessError<*>> {
  val binary = sdk.asBinToExecute().getOr {
    return Result.failure(ExecuteGetProcessError.EnvironmentError(it.error))
  }
  return executeGetProcess(binary, args, scopeToBind, options)
}

/**
 * Converts SDK to [BinOnTarget] to be used by [ExecService]
 */
@ApiStatus.Internal
fun Sdk.asBinToExecute(): Result<BinaryToExec, MessageError> {
  val binaryToExec = when (val additionalData = getOrCreateAdditionalData()) {
    is PyTargetAwareAdditionalData -> {
      additionalData.targetEnvironmentConfiguration?.let { target ->
        BinOnTarget(
          configureTargetCmdLine = this::configureBuilderToRunPythonOnTarget,
          target = target,
        )
      }
    }
    else -> homePath?.let {
      try {
        BinOnEel(Path.of(it))
      }
      catch (e: InvalidPathException) {
        LOGGER.warn("Can't convert ${homePath} to path", e)
        null
      }
    }
  }

  return binaryToExec?.let { PyResult.success(it) }
         ?: PyResult.localizedError(PyBundle.message("python.sdk.broken.configuration", name))
}
