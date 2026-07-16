// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonHomePath
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.frameActivationCache.CacheKeys
import com.jetbrains.python.frameActivationCache.getOrComputeOnFrameActivation
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.impl.enrichLocalPythonSdkWithHomeInfo
import com.jetbrains.python.sdk.impl.pythonEnvironmentCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

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
 * Obtain one via:
 *  - [Sdk.pythonInterpreter] — background-thread factory that triggers detection when the cache is cold;
 *    always returns a non-null wrapper.
 *  - [Sdk.pythonInterpreterOrNull] — synchronous read; returns `null` only when nothing has ever been
 *    cached for the SDK.
 */
@ApiStatus.Internal
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
    get() = (pythonEnvironment as? HasPythonHome)?.pythonHomePath

  val isActivatable: Boolean
    get() = pythonEnvironment is Activatable

  override fun equals(other: Any?): Boolean {
    return sdk == (other as? PythonInterpreter)?.sdk
  }

  override fun hashCode(): Int {
    return sdk.hashCode()
  }
}

/**
 * Python version.
 * SDK is either invalid (`python --version` returned an error) or has a [LanguageLevel].
 * Use `when` to check it.
 */
@ApiStatus.Internal
suspend fun PythonInterpreter.getVersion(): PyResult<LanguageLevel> =
  pythonEnvironment?.validationInfo?.version
  ?:
  // We should have used `pythonEnvironment`, but it doesn't work for remote machines (yet) and we still want to cache version
  // So we cache it until the user activates IDE
  sdk.getOrComputeOnFrameActivation(PY_SDK_LANG_LEVEL_CACHE_KEY) {
    sdk.validatePythonAndGetInfo().version
  }

/**
 * A [PythonInterpreter] wrapping whatever [PythonEnvironment] detection has already cached for this SDK,
 * or `null` when nothing has ever been cached (early startup, non-Python / remote SDK).
 *
 * Synchronous, no I/O. A non-null wrapper whose [PythonInterpreter.environmentResult] is a failure still
 * counts as "already attempted". For a guaranteed fresh result, call [pythonInterpreter] on a background
 * thread.
 */
@get:ApiStatus.Internal
val Sdk.pythonInterpreterOrNull: PythonInterpreter?
  get() = pythonEnvironmentCache?.let { PythonInterpreter(this, it) }

/**
 * Ensures this SDK is enriched with [PythonEnvironment] information and returns a [PythonInterpreter].
 *
 * Always returns a non-null wrapper — inspect [PythonInterpreter.environmentResult] to distinguish:
 *  - `null` — the SDK is non-Python or remote and was not enriched;
 *  - failure — detection ran but failed (e.g. bad home path);
 *  - success — the [PythonEnvironment] was detected.
 *
 * After a successful call on a local Python SDK, subsequent [pythonInterpreterOrNull] reads on this SDK
 * are non-null until the cache is refreshed.
 *
 * Triggers file I/O on the calling thread via the underlying detector; must not be called on EDT.
 *
 * @param forceRefresh re-detect even if a cached result already exists.
 */
@ApiStatus.Internal
@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
fun Sdk.pythonInterpreter(forceRefresh: Boolean = false): PythonInterpreter {
  return PythonInterpreter(this, enrichLocalPythonSdkWithHomeInfo(forceRefresh))
}

@ApiStatus.Internal
suspend fun Sdk.pythonInterpreterAsync(forceRefresh: Boolean = false): PythonInterpreter = withContext(Dispatchers.IO) {
  this@pythonInterpreterAsync.pythonInterpreter(forceRefresh)
}

private val PY_SDK_LANG_LEVEL_CACHE_KEY = CacheKeys<PyResult<LanguageLevel>>("PythonSdkLang")
