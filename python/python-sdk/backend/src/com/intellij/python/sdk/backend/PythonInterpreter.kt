// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonHomePath
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.sdk.findPythonSdk
import com.intellij.python.sdk.backend.impl.enrichLocalPythonSdkWithHomeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * An [Sdk] paired with the outcome of [PythonEnvironment] detection.
 *
 * [PythonInterpreter] has a snaphost of cached environment info - [environmentResult]:
 *  - `null` — nothing has been detected (non-Python / remote SDK);
 *  - [PyResult] failure — detection ran but failed (bad home path, unreadable layout, …);
 *  - [PyResult] success — the detected [PythonEnvironment].
 *
 * Convenience accessors ([pythonEnvironment], [pythonHomePath], [pythonBinaryPath]) reach into the successful result
 * and return `null` for all other cases, so callers that only want the common path can use them
 * without unwrapping.
 *
 * Obtain one via [Sdk.pythonInterpreter], a background-thread factory that triggers detection when the cache is cold
 * and always returns a non-null wrapper.
 *
 * Ask [getPythonInfo] what the interpreter is, and why it cannot be used when it cannot. That is the interpreter's
 * own answer, so an SDK is never asked instead.
 */
class PythonInterpreter internal constructor(
  internal val sdk: Sdk,
  val environmentResult: PyResult<PythonEnvironment>?,
) {
  /** The detected environment on success; `null` when [environmentResult] is `null` or a failure. */
  val pythonEnvironment: PythonEnvironment?
    get() = environmentResult?.successOrNull

  /** Absolute path to the Python interpreter executable from the detected environment; `null` when detection did not succeed. */
  val pythonBinaryPath: PythonBinary?
    get() = pythonEnvironment?.pythonBinaryPath

  /** Environment root (venv / conda prefix) when the detected environment has one; `null` otherwise. */
  val pythonHomePath: PythonHomePath?
    get() = pythonEnvironment?.pythonHomePath

  val isActivatable: Boolean
    get() = pythonEnvironment?.isActivatable == true
}

/**
 * Ensures this SDK is enriched with [PythonEnvironment] information and returns a [PythonInterpreter].
 *
 * Always returns a non-null wrapper — inspect [PythonInterpreter.environmentResult] to distinguish:
 *  - `null` — the SDK is non-Python or remote and was not enriched;
 *  - failure — detection ran but failed (e.g. bad home path);
 *  - success — the [PythonEnvironment] was detected.
 *
 * Triggers file I/O on the calling thread via the underlying detector; must not be called on EDT.
 *
 * @param forceRefresh re-detect even if a cached result already exists.
 */
@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
fun Sdk.pythonInterpreter(forceRefresh: Boolean = false): PythonInterpreter {
  return PythonInterpreter(this, enrichLocalPythonSdkWithHomeInfo(forceRefresh))
}

suspend fun Sdk.pythonInterpreterAsync(forceRefresh: Boolean = false): PythonInterpreter = withContext(Dispatchers.IO) {
  this@pythonInterpreterAsync.pythonInterpreter(forceRefresh)
}


/**
 * Get [PythonInterpreter] if [PyProject] has it
 */
suspend fun PyProject.getInterpreter(): PythonInterpreter? = residesOnModule.findPythonSdk()?.pythonInterpreterAsync()
