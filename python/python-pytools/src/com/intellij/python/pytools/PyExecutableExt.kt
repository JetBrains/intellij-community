// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.python.community.execService.DownloadConfig
import com.intellij.python.community.execService.ProcessOutputTransformer
import com.intellij.python.community.execService.UploadConfig
import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.impl.PySdkBundle
import com.jetbrains.python.sdk.runExecutableWithProgress
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

/**
 * Resolve this executable on [fileSystem]: an SDK-provided [pathFromSdk] first (if valid), then the
 * per-machine custom path / detection cache via [PyExecutableCache]. A machine-less (legacy Target)
 * filesystem has no cache/custom key, so it falls back to plain detection.
 */
suspend fun <P : PathHolder> PyExecutable.resolveExecutable(
  fileSystem: FileSystem<P>,
  pathFromSdk: FullPathOnTarget? = null,
): P? {
  pathFromSdk?.let { fileSystem.parsePath(it).successOrNull }?.let { return it }
  val eelDescriptor = fileSystem.eelDescriptor ?: return fileSystem.detectTool(this)
  val path = PyExecutableCache.getInstance().get(eelDescriptor, this) ?: return null
  return fileSystem.parsePath(path.toString()).successOrNull
}

/**
 * Detect [executable] on [fileSystem] using its [PyExecutable.toolCommandSpec]. The pytools-side entry
 * point for the `FileSystem` detection primitive, so the module works with [PyExecutable] rather than
 * raw `ToolCommandSpec`.
 */
internal suspend fun <P : PathHolder> FileSystem<P>.detectTool(
  executable: PyExecutable,
  filter: (P) -> Boolean = { true },
): P? = detectTool(executable.toolCommandSpec, filter)

/** Resolve (via [resolveExecutable]) then run this executable with [args], returning [transformer]'s result. */
suspend fun <P : PathHolder, T> PyExecutable.runTool(
  fileSystem: FileSystem<P>,
  pathFromSdk: FullPathOnTarget?,
  dirPath: Path?,
  vararg args: String,
  env: Map<String, String> = emptyMap(),
  uploadConfig: UploadConfig? = null,
  downloadConfig: DownloadConfig? = null,
  transformer: ProcessOutputTransformer<T>,
): PyResult<T> {
  val executable = resolveExecutable(fileSystem, pathFromSdk)
                   ?: return PyResult.localizedError(PySdkBundle.message("cannot.find.executable", fusId, fileSystem.userReadableName))
  val bin = fileSystem.getBinaryToExec(executable, dirPath)
  return runExecutableWithProgress(
    binaryToExec = bin,
    timeout = 10.minutes,
    env = env,
    args = args,
    uploadConfig = uploadConfig,
    downloadConfig = downloadConfig,
    transformer = transformer,
  )
}

/** [runTool] returning raw stdout of a zero-exit-code run. */
suspend fun <P : PathHolder> PyExecutable.runTool(
  fileSystem: FileSystem<P>,
  pathFromSdk: FullPathOnTarget?,
  dirPath: Path?,
  vararg args: String,
  env: Map<String, String> = emptyMap(),
  uploadConfig: UploadConfig? = null,
  downloadConfig: DownloadConfig? = null,
): PyResult<String> =
  runTool(
    fileSystem,
    pathFromSdk,
    dirPath,
    args = args,
    env = env,
    uploadConfig = uploadConfig,
    downloadConfig = downloadConfig,
    transformer = ZeroCodeStdoutTransformer,
  )

/**
 * Detect a bare executable [name] on [fileSystem] (`PATH` + the common per-user dirs). For executables
 * that have no [PyExecutable] identity — e.g. `uvx`, or an ad-hoc tool name — so there is no custom
 * path or cache involved.
 */
suspend fun <P : PathHolder> detectExecutableInPath(fileSystem: FileSystem<P>, name: String): P? =
  fileSystem.detectTool(pyExecutable(name))
