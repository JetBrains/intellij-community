package com.jetbrains.python

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.getOr
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.utils.EelProcessExecutionResult
import com.intellij.platform.eel.provider.utils.exec
import com.intellij.platform.eel.provider.utils.stderrString
import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PySdkBundle.message
import com.jetbrains.python.Result.Companion.failure
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor.PYTHON_VERSION_ARG
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor.getLanguageLevelFromVersionStringStaticSafe
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.seconds


/**
 * Ensures that this python is executable and returns its version. Error if python is broken.
 *
 * Some pythons might be broken: they may be executable, even return a version, but still fail to execute it.
 * As we need workable pythons, we validate it by executing
 */
@ApiStatus.Internal
suspend fun PythonBinary.validatePythonAndGetVersion(): Result<LanguageLevel, @NlsSafe String> = withContext(Dispatchers.IO) {
  val smokeTestOutput = executeWithResult("-c", "print(1)").getOr { return@withContext it }.stdoutString.trim()
  if (smokeTestOutput != "1") {
    return@withContext failure(message("python.get.version.error", pathString, smokeTestOutput))
  }

  val versionOutput = executeWithResult(PYTHON_VERSION_ARG).getOr { return@withContext it }
  // Python 2 might return version as stderr, see https://bugs.python.org/issue18338
  val versionString = versionOutput.stdoutString.let { it.ifBlank { versionOutput.stderrString } }
  val languageLevel = getLanguageLevelFromVersionStringStaticSafe(versionString.trim())
  if (languageLevel == null) {
    return@withContext failure(message("python.get.version.wrong.version", pathString, versionOutput))
  }
  return@withContext Result.success(languageLevel)
}

/**
 * Executes [this] with [args], returns either output or error (if execution failed or exit code != 0)
 */
private suspend fun PythonBinary.executeWithResult(vararg args: String): Result<@NlsSafe EelProcessExecutionResult, @NlsSafe String> {
  val output = exec(*args, timeout = 5.seconds).getOr {
    val text = it.error?.message ?: message("python.get.version.too.long", pathString)
    return failure(text)
  }
  return if (output.exitCode != 0) {
    failure(message("python.get.version.error", pathString, "code ${output.exitCode}, ${output.stderrString}"))
  }
  else {
    Result.success(output)
  }
}

@RequiresBackgroundThread
fun PythonBinary.resolvePythonHome(): PythonHomePath = when (getEelDescriptor().platform) {
  is EelPlatform.Windows -> parent.takeIf { it.name.lowercase() != "scripts" } ?: parent.parent
  is EelPlatform.Posix -> parent.takeIf { it.name != "bin" } ?: parent.parent
}

@RequiresBackgroundThread
fun PythonHomePath.resolvePythonBinary(): PythonBinary? {
  return VirtualEnvReader(isWindows = getEelDescriptor().platform is EelPlatform.Windows).findPythonInPythonRoot(this)
}
