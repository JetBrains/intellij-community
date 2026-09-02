package com.intellij.python.sdk.backend.impl

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.errorProcessing.PyResult
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonEnvironment
import com.intellij.python.sdk.backend.detectPythonEnvironment
import com.intellij.python.sdk.backend.PySdkBundle.message
import com.jetbrains.python.sdk.legacy.PythonSdkUtil.isPythonSdk
import com.jetbrains.python.sdk.legacy.PythonSdkUtil.isRemote
import com.intellij.python.sdk.backend.pythonInterpreter
import java.nio.file.InvalidPathException
import java.nio.file.Path

private val PYTHON_ENVIRONMENT_RESULT_KEY = Key.create<PyResult<PythonEnvironment>>("PYTHON_ENVIRONMENT_RESULT")

/**
 * Internal cache primitive backing [PythonInterpreter]; call [pythonInterpreter] from outside this file.
 *
 * Detects the [PythonEnvironment] from the file system layout around this SDK's home path and
 * stores the result in the SDK's [UserData][com.intellij.openapi.util.UserDataHolder]. Performs
 * file I/O on the calling thread; must not be called on EDT.
 *
 * Returns `null` for non-Python and remote SDKs, which are never enriched.
 *
 * @param forceRefresh re-detect even if a cached result already exists.
 */
@RequiresBackgroundThread
internal fun Sdk.enrichLocalPythonSdkWithHomeInfo(forceRefresh: Boolean = false): PyResult<PythonEnvironment>? {
  if (!isPythonSdk(this) || isRemote(this)) return null
  if (!forceRefresh) {
    getUserData(PYTHON_ENVIRONMENT_RESULT_KEY)?.let { return it }
  }

  val homePath = homePath

  val result = if (homePath == null) {
    PyResult.localizedError(message("python.sdk.detect.home.path.null"))
  }
  else {
    try {
      Path.of(homePath).detectPythonEnvironment()
    }
    catch (e: InvalidPathException) {
      thisLogger().warn("Invalid Python SDK home path: $homePath", e)
      PyResult.localizedError(message("python.sdk.detect.invalid.home.path", homePath))
    }
  }

  return result.also {
    putUserData(PYTHON_ENVIRONMENT_RESULT_KEY, it)
  }
}
