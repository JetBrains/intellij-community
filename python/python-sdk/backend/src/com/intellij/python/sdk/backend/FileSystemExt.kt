// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.python.community.execService.DownloadConfig
import com.intellij.python.community.execService.ProcessOutputTransformer
import com.intellij.python.community.execService.UploadConfig
import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.intellij.python.pytools.PyExecutable
import com.intellij.python.pytools.PyExecutableCache
import com.intellij.python.pytools.pyExecutable
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.runExecutableWithProgress
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

/*
 * Locating and running a tool executable on a given `FileSystem`.
 *
 * The receiver is the filesystem rather than the executable: what these do is decided by *where* the work happens — a
 * local/Eel machine has a detection cache and custom-path store, a legacy Target has neither — while the executable is
 * only the thing being looked for. `com.intellij.python.pytools` keeps the machine-level half (tool identity, custom
 * paths, detection, install/upgrade) and knows nothing about `FileSystem`.
 */

/**
 * This executable's path on the receiver filesystem, or `null` when it cannot be found there.
 *
 * Resolution order: an SDK-provided [pathFromSdk] if it parses, then the per-machine custom path or cached detection via
 * [PyExecutableCache]. A machine-less (legacy Target) filesystem has no cache or custom-path key, so it goes straight to
 * detection.
 */
suspend fun <P : PathHolder> FileSystem<P>.resolveExecutable(
  executable: PyExecutable,
  pathFromSdk: FullPathOnTarget? = null,
): P? {
  pathFromSdk?.let { parsePath(it).successOrNull }?.let { return it }
  val eelDescriptor = eelDescriptor ?: return detectTool(executable.toolCommandSpec)
  val path = PyExecutableCache.getInstance().get(eelDescriptor, executable) ?: return null
  return parsePath(path.toString()).successOrNull
}

/** Resolves [executable] here (see [resolveExecutable]) and runs it with [args], returning [transformer]'s result. */
suspend fun <P : PathHolder, T> FileSystem<P>.runTool(
  executable: PyExecutable,
  pathFromSdk: FullPathOnTarget?,
  dirPath: Path?,
  vararg args: String,
  env: Map<String, String> = emptyMap(),
  uploadConfig: UploadConfig? = null,
  downloadConfig: DownloadConfig? = null,
  transformer: ProcessOutputTransformer<T>,
): PyResult<T> {
  val resolved = resolveExecutable(executable, pathFromSdk)
                 ?: return PyResult.localizedError(PySdkBundle.message("cannot.find.executable", executable.fusId, userReadableName))
  return runExecutableWithProgress(
    binaryToExec = getBinaryToExec(resolved, dirPath),
    timeout = 10.minutes,
    env = env,
    args = args,
    uploadConfig = uploadConfig,
    downloadConfig = downloadConfig,
    transformer = transformer,
  )
}

/** [runTool] returning the raw stdout of a zero-exit-code run. */
suspend fun <P : PathHolder> FileSystem<P>.runTool(
  executable: PyExecutable,
  pathFromSdk: FullPathOnTarget?,
  dirPath: Path?,
  vararg args: String,
  env: Map<String, String> = emptyMap(),
  uploadConfig: UploadConfig? = null,
  downloadConfig: DownloadConfig? = null,
): PyResult<String> =
  runTool(
    executable,
    pathFromSdk,
    dirPath,
    args = args,
    env = env,
    uploadConfig = uploadConfig,
    downloadConfig = downloadConfig,
    transformer = ZeroCodeStdoutTransformer,
  )

/**
 * Detects a bare executable [name] here (`PATH` plus the common per-user install dirs).
 *
 * For executables with no [PyExecutable] identity — `uvx`, or an ad-hoc tool name — so no custom path or cache is
 * involved.
 */
suspend fun <P : PathHolder> FileSystem<P>.detectExecutableInPath(name: String): P? {
  return detectTool(pyExecutable(name).toolCommandSpec)
}
