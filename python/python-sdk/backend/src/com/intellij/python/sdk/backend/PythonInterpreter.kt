// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.python.sdk.backend.impl.cachedPythonEnvironmentResult
import com.intellij.python.sdk.backend.impl.enrichLocalPythonSdkWithHomeInfo
import com.intellij.python.sdk.common.PyEnvId
import com.intellij.python.sdk.common.PyInterpreterRef
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.PathMappingSettings
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonHomePath
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.remote.PyRemotePathMapper
import com.jetbrains.python.sdk.InvalidSdkException
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.toFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.nio.file.Path

/**
 * A Python interpreter: an [Sdk], the [PyInterpreterRef] that names it, and the PyProject directory it belongs to.
 *
 * The identity is the pair [ownerDir] and [ref]. A ref is unique only inside one PyProject: the `.venv` of two
 * projects has one ref. [ownerDir] tells them apart. The identity does not depend on [sdk], so the registry can adopt
 * an existing SDK for an interpreter without a change to its hash code.
 *
 * The two kinds are an interpreter on an Eel machine, [NativePythonInterpreter], and one on a legacy target,
 * [TargetPythonInterpreter]. Check the kind with an exhaustive `when`.
 *
 * Only a native interpreter has a detected [PythonEnvironment], see [NativePythonInterpreter.environmentResult].
 *
 * Get one with [Sdk.pythonInterpreterResult]. Ask [getPythonInfo] what the interpreter is, and why it cannot be used when it
 * cannot.
 */
sealed class PythonInterpreter {
  /**
   * The ref that names this interpreter in the PyProject of [ownerDir]. The kind gives its mode, so a native
   * interpreter always has a native ref, and a target interpreter a ref on its target.
   */
  abstract val ref: PyInterpreterRef

  /**
   * The base directory of the PyProject this interpreter belongs to, see [projectDirOf]. It is set when the interpreter
   * is built and never changes. A PyProject that appears later at this directory owns the interpreter. Other PyProjects
   * can use it too, but only its owner lists it, see [PythonInterpreterRegistry.interpreters].
   */
  abstract val ownerDir: Path

  /**
   * The SDK of this interpreter. The registry replaces it when it adopts an existing SDK. Code outside this module
   * reaches it through the deprecated [getSdkAPI], so the SDK can be removed later.
   */
  internal var sdk: Sdk

  constructor(sdk: Sdk) {
    this.sdk = sdk
  }

  final override fun equals(other: Any?): Boolean =
    this === other || (other is PythonInterpreter && ref == other.ref && ownerDir == other.ownerDir)

  final override fun hashCode(): Int = 31 * ref.hashCode() + ownerDir.hashCode()

  override fun toString(): String = "PythonInterpreter($ref in $ownerDir)"
}

/** An interpreter on an Eel machine: local, WSL or Docker. */
class NativePythonInterpreter internal constructor(
  sdk: Sdk,
  env: PyEnvId,
  override val ownerDir: Path,
  /**
   * The Python binary of this interpreter. It is known also when [environmentResult] is a failure, unlike
   * [pythonBinaryPath].
   */
  val pythonBinary: PythonBinary,
  private val detectedOnBuild: PyResult<PythonEnvironment>,
) : PythonInterpreter(sdk) {
  override val ref: PyInterpreterRef = PyInterpreterRef(PyInterpreterRef.Mode.Native, env)

  /**
   * The detected environment: a failure when detection ran and failed, for example for a bad home path, else the
   * [PythonEnvironment] found.
   *
   * The last detection for the SDK wins over the detection when this interpreter was built. The registry and the
   * project model keep one interpreter while the SDK stays in the table. A tool can recreate the environment on another
   * Python in that time, and the SDK update then detects it again. Without this, every holder showed the old version
   * until a restart (PY-93116).
   */
  val environmentResult: PyResult<PythonEnvironment>
    get() = sdk.cachedPythonEnvironmentResult() ?: detectedOnBuild

  /** The detected environment on success; `null` when [environmentResult] is a failure. */
  val pythonEnvironment: PythonEnvironment?
    get() = environmentResult.successOrNull

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
 * The detected environment of a native interpreter, or `null` for a target or when detection failed. For code that
 * only needs the environment when there is one, such as Java code, which cannot use an exhaustive `when`.
 */
val PythonInterpreter.detectedEnvironment: PythonEnvironment?
  get() = when (this) {
    is NativePythonInterpreter -> pythonEnvironment
    is TargetPythonInterpreter -> null
  }

/** The environment root of a native interpreter, see [NativePythonInterpreter.pythonHomePath]. `null` for a target. */
val PythonInterpreter.detectedHomePath: PythonHomePath?
  get() = detectedEnvironment?.pythonHomePath

/** Whether this is a native interpreter whose environment can be activated. `false` for a target. */
val PythonInterpreter.isDetectedActivatable: Boolean
  get() = detectedEnvironment?.isActivatable == true

/** An interpreter on a legacy target, such as SSH or a Docker target. Its environment is not detected. */
class TargetPythonInterpreter internal constructor(
  sdk: Sdk,
  targetId: @NonNls String,
  env: PyEnvId,
  override val ownerDir: Path,
  /** The Python binary of this interpreter on the target. */
  val pythonBinary: PathHolder.Target,
) : PythonInterpreter(sdk) {
  override val ref: PyInterpreterRef = PyInterpreterRef(PyInterpreterRef.Mode.Target(targetId), env)
}

/**
 * The interpreter of this SDK. The SDK data gives its ref and the PyProject directory it belongs to, see
 * [interpreterRefOf] and [projectDirOf]. A native interpreter also gets its [PythonEnvironment] detected.
 *
 * A failure when this SDK names no interpreter: it is not a Python SDK, it has no home path, it has no owner directory,
 * or it is a target SDK without a target configuration. The registry adopts an SDK without an owner directory, see
 * [PythonInterpreterRegistry.registerPythonInterpreter]. The user can only recreate any other such SDK.
 *
 * Triggers file I/O on the calling thread via the underlying detector; must not be called on EDT.
 *
 * @param forceRefresh re-detect even if a cached result already exists.
 */
@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
fun Sdk.pythonInterpreterResult(forceRefresh: Boolean = false): PyResult<PythonInterpreter> = buildPythonInterpreter(forceRefresh)

/** The body of [pythonInterpreterResult]. It does file I/O, so it runs on a background thread. */
@RequiresBackgroundThread(generateAssertion = false)
private fun Sdk.buildPythonInterpreter(forceRefresh: Boolean): PyResult<PythonInterpreter> {
  val data = sdkAdditionalData as? PythonSdkAdditionalData
  val ref = interpreterRefOf(this)
  val homePath = pathEnvRef(this)
  if (data == null || ref == null || homePath == null) {
    return PyResult.localizedError(PySdkBundle.message("python.sdk.broken.configuration", name))
  }
  // An SDK without an owner is no interpreter. The registry adopts such an SDK for the first PyProject that needs it.
  val ownerDir = projectDirOf(data) ?: return PyResult.localizedError(PySdkBundle.message("python.sdk.broken.configuration", name))
  val interpreter = when (val mode = ref.mode) {
    is PyInterpreterRef.Mode.Target -> TargetPythonInterpreter(this, mode.targetId, ref.env, ownerDir, PathHolder.Target(homePath))
    PyInterpreterRef.Mode.Native -> {
      val binary = homePath.toNioPathOrNull()
                   ?: return PyResult.localizedError(PySdkBundle.message("python.sdk.broken.configuration", name))
      // `null` only for remote data, which is target data, so a native SDK always gets a result.
      val environment = enrichLocalPythonSdkWithHomeInfo(forceRefresh)
                        ?: return PyResult.localizedError(PySdkBundle.message("python.sdk.broken.configuration", name))
      NativePythonInterpreter(this, ref.env, ownerDir, binary, environment)
    }
  }
  return PyResult.success(interpreter)
}

/**
 * The interpreter of this SDK of the interpreter store, with the owner directory and the ref of [key], see
 * [PythonInterpreterRecords]. The key names the interpreter, so no change of the SDK data, such as its association,
 * moves it. Only the binary and the environment come from the SDK. A failure when the SDK has no home path, or when
 * its mode differs from the mode of [key].
 */
internal suspend fun Sdk.pythonInterpreterOfKey(key: PythonInterpreterKey): PyResult<PythonInterpreter> = withContext(Dispatchers.IO) {
  val sdk = this@pythonInterpreterOfKey
  val homePath = pathEnvRef(sdk) ?: return@withContext PyResult.localizedError(PySdkBundle.message("python.sdk.broken.configuration", name))
  val interpreter = when (val mode = key.ref.mode) {
    is PyInterpreterRef.Mode.Target -> TargetPythonInterpreter(sdk, mode.targetId, key.ref.env, key.ownerDir, PathHolder.Target(homePath))
    PyInterpreterRef.Mode.Native -> {
      val binary = homePath.toNioPathOrNull()
                   ?: return@withContext PyResult.localizedError(PySdkBundle.message("python.sdk.broken.configuration", name))
      val environment = enrichLocalPythonSdkWithHomeInfo(false)
                        ?: return@withContext PyResult.localizedError(PySdkBundle.message("python.sdk.broken.configuration", name))
      NativePythonInterpreter(sdk, key.ref.env, key.ownerDir, binary, environment)
    }
  }
  PyResult.success(interpreter)
}

/**
 * [Sdk.pythonInterpreterResult] for a caller that can suspend. This is the main entry point.
 *
 * The detection runs on [Dispatchers.IO] and caches its result per SDK, so only the first call pays for the file
 * reads. It takes no lock and never touches the EDT.
 *
 * @param forceRefresh re-detect even if a cached result already exists.
 */
suspend fun Sdk.pythonInterpreterResultAsync(forceRefresh: Boolean = false): PyResult<PythonInterpreter> = withContext(Dispatchers.IO) {
  this@pythonInterpreterResultAsync.buildPythonInterpreter(forceRefresh)
}


@ApiStatus.Internal
suspend fun PythonInterpreter.toFileSystem(): FileSystem<*> = sdk.toFileSystem()

/**
 * Creates the [PyRemotePathMapper] of this interpreter with the user-defined [console] mappings.
 * The [FileSystem] and the [Sdk] that [FileSystem.createMapper] needs come from this one interpreter.
 */
@ApiStatus.Internal
suspend fun PythonInterpreter.createMapper(project: Project, console: PathMappingSettings): PyRemotePathMapper =
  toFileSystem().createMapper(project, console, sdk)

/**
 * [Sdk.pythonInterpreterResult] for a Java caller that reports a broken SDK as an [InvalidSdkException], with the message
 * of the failure.
 */
@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
@Throws(InvalidSdkException::class)
fun Sdk.pythonInterpreterOrThrow(forceRefresh: Boolean = false): PythonInterpreter =
  pythonInterpreterResult(forceRefresh).getOr { throw InvalidSdkException(it.error.message) }

/**
 * The interpreter of this SDK, for a caller that does not handle a broken SDK yet. It throws [InvalidSdkException] for
 * an SDK that names no interpreter.
 */
@Deprecated("Handle a broken SDK", ReplaceWith("pythonInterpreterResult(forceRefresh)"))
@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
@Throws(InvalidSdkException::class)
fun Sdk.pythonInterpreter(forceRefresh: Boolean = false): PythonInterpreter = pythonInterpreterOrThrow(forceRefresh)

/** [Sdk.pythonInterpreter] for a caller that can suspend. */
@Deprecated("Handle a broken SDK", ReplaceWith("pythonInterpreterResultAsync(forceRefresh)"))
suspend fun Sdk.pythonInterpreterAsync(forceRefresh: Boolean = false): PythonInterpreter =
  pythonInterpreterResultAsync(forceRefresh).getOr { throw InvalidSdkException(it.error.message) }

/** The detected environment, see [NativePythonInterpreter.pythonEnvironment]. `null` for a target. */
@Deprecated("Use detectedEnvironment", ReplaceWith("detectedEnvironment"))
val PythonInterpreter.pythonEnvironment: PythonEnvironment?
  get() = detectedEnvironment

/** The detected Python binary, see [NativePythonInterpreter.pythonBinaryPath]. `null` for a target. */
@Deprecated("Use detectedEnvironment", ReplaceWith("detectedEnvironment?.pythonBinaryPath"))
val PythonInterpreter.pythonBinaryPath: PythonBinary?
  get() = detectedEnvironment?.pythonBinaryPath
