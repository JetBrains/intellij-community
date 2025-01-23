package com.jetbrains.python

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.getOr
import com.intellij.platform.eel.impl.utils.exec
import com.jetbrains.python.PySdkBundle.message
import com.jetbrains.python.Result.Companion.failure
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor.PYTHON_VERSION_ARG
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor.getVersionStringFromOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.seconds


// TODO: PythonInterpreterService: validate system python
/**
 * Ensures that this python is executable and returns its version. Error if python is broken.
 *
 * Some pythons might be broken: they may be executable, even return a version, but still fail to execute it.
 * As we need workable pythons, we validate it by executing
 */
@ApiStatus.Internal
suspend fun PythonBinary.validatePythonAndGetVersion(): Result<LanguageLevel, @NlsSafe String> = withContext(Dispatchers.IO) {
  val smokeTestOutput = executeWithResult("-c", "print(1)").getOr { return@withContext it }.trim()
  if (smokeTestOutput != "1") {
    return@withContext failure(message("python.get.version.error", pathString, smokeTestOutput))
  }

  val versionString = executeWithResult(PYTHON_VERSION_ARG).getOr { return@withContext it }
  val languageLevel = getVersionStringFromOutput(versionString)?.let {
    LanguageLevel.fromPythonVersion(it)
  }
  if (languageLevel == null) {
    return@withContext failure(message("python.get.version.wrong.version", pathString, versionString))
  }
  return@withContext Result.success(languageLevel)
}

/**
 * Executes [this] with [args], returns either stdout or error (if execution failed or exit code != 0)
 */
@ApiStatus.Internal
suspend fun PythonBinary.executeWithResult(vararg args: String): Result<@NlsSafe String, @NlsSafe String> {
  val output = exec(*args, timeout = 5.seconds).getOr {
    val text = it.error?.message ?: message("python.get.version.too.long", pathString)
    return failure(text)
  }
  return if (output.exitCode != 0) {
    failure(message("python.get.version.error", pathString, "code ${output.exitCode}, {output.stderr}"))
  }
  else {
    Result.success(output.stdout)
  }
}