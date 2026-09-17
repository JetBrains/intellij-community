// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.impl

import com.intellij.openapi.diagnostic.thisLogger
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
import com.intellij.python.community.execService.python.PyHelper
import com.intellij.python.community.execService.python.StdInProvider
import com.intellij.python.community.execService.python.executeHelper
import com.intellij.python.sdk.backend.PySdkBundle
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.configureBuilderToRunPythonOnTarget
import com.jetbrains.python.sdk.pySdkAdditionalData
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.CheckReturnValue
import java.nio.file.InvalidPathException
import java.nio.file.Path

// See function it calls for more info
@CheckReturnValue
internal suspend fun ExecService.execGetStdoutImpl(
  sdk: Sdk,
  args: Args,
  options: ExecOptions = ExecOptions(),
  procListener: PyProcessListener? = null,
): PyResult<String> {
  val binToExecute = sdk.asBinToExecuteImpl().getOr { return it }
  return execGetStdout(binToExecute, args, options, procListener)
}

// See function it calls for more info
@CheckReturnValue
internal suspend fun <T> ExecService.executeImpl(
  sdk: Sdk,
  args: Args = Args(),
  options: ExecOptions = ExecOptions(),
  procListener: PyProcessListener? = null,
  processOutputTransformer: ProcessOutputTransformer<T>,
): PyResult<T> {
  val binToExecute = sdk.asBinToExecuteImpl().getOr { return it }
  return execute(binToExecute, args, options, procListener, processOutputTransformer = processOutputTransformer)
}

/**
 * Executes [helper] on [sdk] (copies it to the remote machine if needed)
 * To write something into the `stdin` of [helper], use [stdInProvider].
 */
@CheckReturnValue
internal suspend fun ExecService.executeHelperImpl(
  sdk: Sdk,
  helper: PyHelper,
  helperArgs: Args = Args(),
  options: ExecOptions = ExecOptions(),
  procListener: PyProcessListener? = null,
  stdInProvider: StdInProvider? = null,
): PyResult<String> {
  val binToExecute = sdk.asBinToExecuteImpl().getOr { return it }
  return executeHelper(binToExecute, helper, helperArgs, options, procListener, stdInProvider)
}

// See function it calls for more info
@CheckReturnValue
internal suspend fun ExecService.executeGetProcessImpl(
  sdk: Sdk,
  args: Args = Args(),
  scopeToBind: CoroutineScope? = null,
  options: ExecGetProcessOptions = ExecGetProcessOptions(),
): Result<Process, ExecuteGetProcessError<*>> {
  val binary = sdk.asBinToExecuteImpl().getOr {
    return Result.failure(ExecuteGetProcessError.EnvironmentError(it.error))
  }
  return executeGetProcess(binary, args, scopeToBind, options)
}

/**
 * Converts SDK to [com.intellij.python.community.execService.BinOnTarget] to be used by [ExecService]
 *
 * This reads the SDK's own home path, or its target data. The detection result of
 * [com.intellij.python.sdk.backend.PythonInterpreter] plays no part here. A failed detection does not stop the
 * execution. Ask [com.intellij.python.sdk.backend.getPythonInfo] first when the caller must know that the interpreter
 * works.
 */
internal fun Sdk.asBinToExecuteImpl(): Result<BinaryToExec, MessageError> {
  val binaryToExec = when (val additionalData = pySdkAdditionalData) {
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
        thisLogger().warn("Can't convert ${homePath} to path", e)
        null
      }
    }
  }

  return binaryToExec?.let { PyResult.success(it) } ?: PyResult.localizedError(PySdkBundle.message("python.sdk.broken.configuration", name))
}