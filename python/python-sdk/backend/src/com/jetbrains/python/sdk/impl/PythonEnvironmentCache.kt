package com.jetbrains.python.sdk.impl

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.PythonInterpreter
import com.jetbrains.python.sdk.PythonEnvironment
import com.jetbrains.python.sdk.detectPythonEnvironment
import com.jetbrains.python.sdk.impl.PySdkBundle.message
import com.jetbrains.python.sdk.legacy.PythonSdkUtil.isPythonSdk
import com.jetbrains.python.sdk.legacy.PythonSdkUtil.isRemote
import com.jetbrains.python.sdk.pythonInterpreter
import com.jetbrains.python.sdk.pythonInterpreterOrNull
import java.nio.file.InvalidPathException
import java.nio.file.Path

private val PYTHON_ENVIRONMENT_RESULT_KEY = Key.create<PyResult<PythonEnvironment>>("PYTHON_ENVIRONMENT_RESULT")

/**
 * Internal cache primitive backing [PythonInterpreter]; call [pythonInterpreter] (or [pythonInterpreterOrNull] for the
 * synchronous cached view) from outside this file.
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

/**
 * The cached [PythonEnvironment] detection result for this SDK, or `null` when nothing has ever
 * been written to the cache (the SDK has never been enriched, or is non-Python / remote).
 * A non-null entry may still be a failure; callers that only want success should either unwrap
 * with `successOrNull` or go through [PythonInterpreter].
 *
 * Module-internal accessor backing [PythonInterpreter]; other consumers should go through [pythonInterpreterOrNull]
 * (sync) or [pythonInterpreter] (background-thread enrichment).
 */
internal val Sdk.pythonEnvironmentCache: PyResult<PythonEnvironment>?
  get() = getUserData(PYTHON_ENVIRONMENT_RESULT_KEY)