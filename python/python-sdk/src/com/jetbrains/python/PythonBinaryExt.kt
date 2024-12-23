package com.jetbrains.python

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.util.io.awaitExit
import com.jetbrains.python.PySdkBundle.message
import com.jetbrains.python.Result.Companion.failure
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds


// TODO: PythonInterpreterService: validate system python
/**
 * Ensures that this python is executable and returns its version. Error if python is broken (reports error to logs as well).
 *
 * Some pythons might be broken: they may be executable, even return a version, but still fail to execute it.
 * As we need workable pythons, we validate it by executing
 */
@ApiStatus.Internal
suspend fun PythonBinary.validatePythonAndGetVersion(): Result<LanguageLevel, LocalizedErrorString> = withContext(Dispatchers.IO) {
  val fileLogger = fileLogger()
  val process =
    try {
      GeneralCommandLine(pathString, "-c", "print(1)").createProcess()
    }
    catch (e: ExecutionException) {
      val error = message("python.get.version.error", pathString, e.message)
      fileLogger.warn(error, e)
      return@withContext failure(LocalizedErrorString(error))
    }
  val timeout = 5.seconds
  val exitCode = withTimeoutOrNull(timeout) {
    process.awaitExit()
  }
  when (exitCode) {
    null -> {
      fileLogger.warn("$this didn't return in $timeout, skipping")
    }
    0 -> {
      val pythonVersion = PythonSdkFlavor.getVersionStringStatic(pathString)
                          ?: return@withContext failure(LocalizedErrorString(message("python.get.version.wrong.version", pathString, "")))
      val languageLevel = LanguageLevel.fromPythonVersion(pythonVersion)
      if (languageLevel == null) {
        fileLogger.warn("$pythonVersion is not valid version")
        return@withContext failure(LocalizedErrorString(message("python.get.version.wrong.version", pathString, "")))
      }
      return@withContext Result.success(languageLevel)
    }
    else -> {
      fileLogger.warn("$this exited with code ${exitCode}, skipping")
    }
  }
  process.destroyForcibly()
  if (withTimeoutOrNull(500.milliseconds) {
      process.awaitExit()
    } == null) {
    fileLogger.warn("Process $process still running, might be leaked")
  }
  return@withContext failure(LocalizedErrorString(message("python.get.version.error", pathString, exitCode)))
}

