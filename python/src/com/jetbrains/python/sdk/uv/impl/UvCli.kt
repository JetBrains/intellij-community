// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv.impl

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.execService.DownloadConfig
import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.pathValidation.PlatformAndRoot
import com.jetbrains.python.pathValidation.ValidationRequest
import com.jetbrains.python.pathValidation.validateExecutableFile
import com.jetbrains.python.sdk.ToolCommandExecutor
import com.jetbrains.python.sdk.add.v2.EelFileSystem
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.runExecutableWithProgress
import com.jetbrains.python.sdk.uv.UvCli
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.minutes

private const val UV_PATH_SETTING: String = "PyCharm.Uv.Path"

private var PropertiesComponent.uvPath: Path?
  get() {
    return getValue(UV_PATH_SETTING)?.let { Path.of(it) }
  }
  set(value) {
    setValue(UV_PATH_SETTING, value.toString())
  }

private val UV_TOOL: ToolCommandExecutor = ToolCommandExecutor(
  "uv",
  getToolPathFromSettings = { uvPath?.pathString }
)

private fun <P : PathHolder> validateUvExecutable(uvPath: P?, platformAndRoot: PlatformAndRoot): ValidationInfo? {
  return validateExecutableFile(ValidationRequest(
    path = uvPath?.toString(),
    fieldIsEmpty = PyBundle.message("python.sdk.uv.executable.not.found"),
    platformAndRoot = platformAndRoot
  ))
}

private suspend fun <P : PathHolder> runUv(
  uv: P,
  workingDir: Path,
  venvPath: P?,
  fileSystem: FileSystem<P>,
  canChangeTomlOrLock: Boolean,
  vararg args: String,
): PyResult<String> {
  val env = buildMap {
    if (venvPath == null) {
      put("VIRTUAL_ENV", VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME)
    }
    else {
      put("VIRTUAL_ENV", venvPath.toString())
    }
    venvPath?.let { put("UV_PROJECT_ENVIRONMENT", it.toString()) }
  }
  val bin = fileSystem.getBinaryToExec(uv, workingDir)
  val downloadConfig = if (canChangeTomlOrLock) DownloadConfig(relativePaths = listOf("pyproject.toml", "uv.lock")) else null
  return runExecutableWithProgress(bin,
                                   env = env,
                                   timeout = 10.minutes,
                                   args = args,
                                   transformer = ZeroCodeStdoutTransformer,
                                   downloadConfig = downloadConfig)
}

private class UvCliImpl<P : PathHolder>(val dispatcher: CoroutineDispatcher, val uv: P, private val fileSystem: FileSystem<P>) : UvCli<P> {

  override suspend fun runUv(workingDir: Path, venvPath: P?, canChangeTomlOrLock: Boolean, vararg args: String): PyResult<String> =
    withContext(dispatcher) {
      runUv(uv, workingDir, venvPath, fileSystem, canChangeTomlOrLock, *args)
    }
}

suspend fun getUvExecutableLocal(eel: EelApi = localEel): Path? = getUvExecutable(EelFileSystem(eel), null)?.path

internal suspend fun <P : PathHolder> getUvExecutable(fileSystem: FileSystem<P>, pathFromSdk: FullPathOnTarget?): P? =
  UV_TOOL.getToolExecutable(fileSystem, pathFromSdk)

fun setUvExecutableLocal(path: Path) {
  PropertiesComponent.getInstance().uvPath = path
}

suspend fun hasUvExecutableLocal(): Boolean {
  return getUvExecutableLocal() != null
}

internal suspend fun createUvCliLocal(uv: Path? = null, dispatcher: CoroutineDispatcher = Dispatchers.IO): PyResult<UvCli<PathHolder.Eel>> {
  return createUvCli(uv?.let { PathHolder.Eel(it) }, EelFileSystem(localEel), dispatcher)
}

internal suspend fun <P : PathHolder> createUvCli(
  uv: P?,
  fileSystem: FileSystem<P>,
  dispatcher: CoroutineDispatcher = Dispatchers.IO,
): PyResult<UvCli<P>> {
  val path = uv ?: getUvExecutable(fileSystem, null)
  val error = validateUvExecutable(path, fileSystem.platformAndRoot)
  return if (error != null) {
    PyResult.localizedError(error.message)
  }
  else PyResult.success(UvCliImpl(dispatcher, path!!, fileSystem))
}