// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.python.impl

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.provider.utils.EelProcessExecutionResult
import com.intellij.platform.eel.provider.utils.stderrString
import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.BinOnTarget
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.ZeroCodeStdoutTransformerBool
import com.intellij.python.community.execService.ZeroCodeStdoutTransformerTyped
import com.intellij.python.community.execService.impl.transformerToHandler
import com.intellij.python.community.execService.python.advancedApi.ExecutablePython
import com.intellij.python.community.execService.python.advancedApi.executePythonAdvanced
import com.intellij.python.community.execService.python.impl.PyExecPythonBundle.message
import com.jetbrains.python.PYTHON_VERSION_ARG
import com.jetbrains.python.PythonInfo
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.getOr
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor.getLanguageLevelFromVersionStringStaticSafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.minutes

private const val SYS_MODULE = "sys"
private const val IS_GIL_ENABLED_FUNCTION = "_is_gil_enabled"
private const val GIL_CHECK_CMD = "from __future__ import print_function; import $SYS_MODULE; print($SYS_MODULE.$IS_GIL_ENABLED_FUNCTION()) if hasattr($SYS_MODULE, '$IS_GIL_ENABLED_FUNCTION') and callable(getattr($SYS_MODULE, '$IS_GIL_ENABLED_FUNCTION')) else print(True)"

@ApiStatus.Internal
internal suspend fun ExecService.validatePythonAndGetInfoImpl(python: ExecutablePython): PyResult<PythonInfo> = withContext(Dispatchers.IO) {
  val options = ExecOptions(timeout = 1.minutes)
  val freeThreaded = !execGetStdoutBoolImpl(python, GIL_CHECK_CMD).getOr(message("python.check.threading.fail")) { return@withContext it }

  val versionOutput: EelProcessExecutionResult = executePythonAdvanced(python, options = options, args = Args(PYTHON_VERSION_ARG), processInteractiveHandler = transformerToHandler<EelProcessExecutionResult>(null) { r ->
    if (r.exitCode == 0) Result.success(r) else Result.failure(message("python.get.version.error", python.userReadableName, r.exitCode))
  }).getOr { return@withContext it }
  // Python 2 might return version as stderr, see https://bugs.python.org/issue18338
  val versionString = versionOutput.stdoutString.let { it.ifBlank { versionOutput.stderrString } }
  val languageLevel = getLanguageLevelFromVersionStringStaticSafe(versionString.trim())
  if (languageLevel == null) {
    return@withContext PyResult.localizedError(message("python.get.version.wrong.version", python.userReadableName, versionString))
  }

  return@withContext Result.success(PythonInfo(languageLevel, freeThreaded))
}

@ApiStatus.Internal
internal suspend fun ExecService.execGetStdoutBoolImpl(python: ExecutablePython, command: @NlsSafe String): PyResult<Boolean> = execGetStdoutImpl(python, command, ZeroCodeStdoutTransformerBool)

@ApiStatus.Internal
internal suspend fun <T : Any> ExecService.execGetStdoutImpl(python: ExecutablePython, command: @NlsSafe String, transformer: ZeroCodeStdoutTransformerTyped<T>): PyResult<T> = withContext(Dispatchers.IO) {
  val options = ExecOptions(timeout = 1.minutes)
  val result = executePythonAdvanced(
    python,
    Args("-c", command),
    processInteractiveHandler = transformerToHandler(null, transformer),
    options = options
  ).getOr(message("python.cannot.exec", python.userReadableName)) { return@withContext it }
  return@withContext Result.success(result)
}

private val ExecutablePython.userReadableName: @NlsSafe String
  get() =
    (listOf(when (binary) {
              is BinOnEel -> binary.path.pathString
              is BinOnTarget -> binary
            }) + args).joinToString(" ")
