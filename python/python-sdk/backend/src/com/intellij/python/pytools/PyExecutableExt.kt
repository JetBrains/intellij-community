// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.python.community.execService.DownloadConfig
import com.intellij.python.community.execService.ProcessOutputTransformer
import com.intellij.python.community.execService.UploadConfig
import com.intellij.python.sdk.backend.detectExecutableInPath
import com.intellij.python.sdk.backend.resolveExecutable
import com.intellij.python.sdk.backend.runTool
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import java.nio.file.Path

/*
 * Deprecated proxies. The logic now lives in `com.intellij.python.sdk.backend`, as extensions on `FileSystem` — the
 * receiver that actually decides the outcome — rather than on the executable being looked for. These forward and exist
 * only so the existing call sites keep compiling; new code should call the `FileSystem` extensions directly.
 */

@Deprecated(
  "Use FileSystem.resolveExecutable instead",
  ReplaceWith("fileSystem.resolveExecutable(this, pathFromSdk)", "com.intellij.python.sdk.backend.resolveExecutable"),
)
suspend fun <P : PathHolder> PyExecutable.resolveExecutable(
  fileSystem: FileSystem<P>,
  pathFromSdk: FullPathOnTarget? = null,
): P? = fileSystem.resolveExecutable(this, pathFromSdk)

@Deprecated(
  "Use FileSystem.runTool instead",
  ReplaceWith(
    "fileSystem.runTool(this, pathFromSdk, dirPath, *args, env = env, uploadConfig = uploadConfig, downloadConfig = downloadConfig, transformer = transformer)",
    "com.intellij.python.sdk.backend.runTool",
  ),
)
suspend fun <P : PathHolder, T> PyExecutable.runTool(
  fileSystem: FileSystem<P>,
  pathFromSdk: FullPathOnTarget?,
  dirPath: Path?,
  vararg args: String,
  env: Map<String, String> = emptyMap(),
  uploadConfig: UploadConfig? = null,
  downloadConfig: DownloadConfig? = null,
  transformer: ProcessOutputTransformer<T>,
): PyResult<T> =
  fileSystem.runTool(
    this,
    pathFromSdk,
    dirPath,
    args = args,
    env = env,
    uploadConfig = uploadConfig,
    downloadConfig = downloadConfig,
    transformer = transformer,
  )

@Deprecated(
  "Use FileSystem.runTool instead",
  ReplaceWith(
    "fileSystem.runTool(this, pathFromSdk, dirPath, *args, env = env, uploadConfig = uploadConfig, downloadConfig = downloadConfig)",
    "com.intellij.python.sdk.backend.runTool",
  ),
)
suspend fun <P : PathHolder> PyExecutable.runTool(
  fileSystem: FileSystem<P>,
  pathFromSdk: FullPathOnTarget?,
  dirPath: Path?,
  vararg args: String,
  env: Map<String, String> = emptyMap(),
  uploadConfig: UploadConfig? = null,
  downloadConfig: DownloadConfig? = null,
): PyResult<String> =
  fileSystem.runTool(
    this,
    pathFromSdk,
    dirPath,
    args = args,
    env = env,
    uploadConfig = uploadConfig,
    downloadConfig = downloadConfig,
  )

@Deprecated(
  "Use FileSystem.detectExecutableInPath instead",
  ReplaceWith("fileSystem.detectExecutableInPath(name)", "com.intellij.python.sdk.backend.detectExecutableInPath"),
)
suspend fun <P : PathHolder> detectExecutableInPath(fileSystem: FileSystem<P>, name: String): P? =
  fileSystem.detectExecutableInPath(name)
