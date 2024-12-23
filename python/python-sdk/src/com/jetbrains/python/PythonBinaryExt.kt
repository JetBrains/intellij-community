package com.jetbrains.python

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.util.io.awaitExit
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
 * Ensures that this python is executable and returns its version. `null` if python is broken (reports error to logs).
 *
 * Some pythons might be broken: they may be executable, even return a version, but still fail to execute it.
 * As we need workable pythons, we validate it by executing
 */
@ApiStatus.Internal
suspend fun PythonBinary.validatePythonAndGetVersion(): LanguageLevel? = withContext(Dispatchers.IO) {
  val fileLogger = fileLogger()
  val process =
    try {
      GeneralCommandLine(pathString, "-c", "print(1)").createProcess()
    }
    catch (e: ExecutionException) {
      fileLogger.warn("$this can't be executed, skipping", e)
      return@withContext null
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
      val pythonVersion = PythonSdkFlavor.getVersionStringStatic(pathString) ?: return@withContext null
      val languageLevel = LanguageLevel.fromPythonVersion(pythonVersion)
      if (languageLevel == null) {
        fileLogger.warn("$pythonVersion is not valid version")
        return@withContext null
      }
      return@withContext languageLevel
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
  return@withContext null
}

