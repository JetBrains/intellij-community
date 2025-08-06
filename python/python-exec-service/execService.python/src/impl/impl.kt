// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.python.impl

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.provider.utils.EelProcessExecutionResult
import com.intellij.platform.eel.provider.utils.stderrString
import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.intellij.python.community.execService.impl.transformerToHandler
import com.intellij.python.community.execService.python.advancedApi.ExecutablePython
import com.intellij.python.community.execService.python.advancedApi.executePythonAdvanced
import com.intellij.python.community.execService.python.impl.PyExecPythonBundle.message
import com.jetbrains.python.PYTHON_VERSION_ARG
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.getOr
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor.getLanguageLevelFromVersionStringStaticSafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.minutes

@ApiStatus.Internal
internal suspend fun ExecService.validatePythonAndGetVersionImpl(python: ExecutablePython): PyResult<LanguageLevel> = withContext(Dispatchers.IO) {
  val options = ExecOptions(timeout = 1.minutes)

  val smokeTestOutput = executePythonAdvanced(python, Args("-c", "print(1)"), processInteractiveHandler = transformerToHandler(null, ZeroCodeStdoutTransformer), options = options).getOr(message("python.cannot.exec", python.userReadableName)) { return@withContext it }.trim()
  if (smokeTestOutput != "1") {
    return@withContext PyResult.localizedError(message("python.get.version.error", python.userReadableName, smokeTestOutput))
  }

  val versionOutput: EelProcessExecutionResult = executePythonAdvanced(python, options = options, args = Args(PYTHON_VERSION_ARG), processInteractiveHandler = transformerToHandler<EelProcessExecutionResult>(null, { r ->
    if (r.exitCode == 0) Result.success(r) else Result.failure(message("python.get.version.error", python.userReadableName, r.exitCode))
  })).getOr { return@withContext it }
  // Python 2 might return version as stderr, see https://bugs.python.org/issue18338
  val versionString = versionOutput.stdoutString.let { it.ifBlank { versionOutput.stderrString } }
  val languageLevel = getLanguageLevelFromVersionStringStaticSafe(versionString.trim())
  if (languageLevel == null) {
    return@withContext PyResult.localizedError(message("python.get.version.wrong.version", python.userReadableName, versionOutput))
  }
  return@withContext Result.success(languageLevel)
}

private val ExecutablePython.userReadableName: @NlsSafe String get() = (listOf(binary.pathString) + args).joinToString(" ")
