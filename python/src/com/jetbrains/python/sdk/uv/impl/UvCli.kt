// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv.impl

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.BinOnTarget
import com.intellij.python.community.execService.DownloadConfig
import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.pathValidation.PlatformAndRoot
import com.jetbrains.python.pathValidation.PlatformAndRoot.Companion.getPlatformAndRoot
import com.jetbrains.python.pathValidation.ValidationRequest
import com.jetbrains.python.pathValidation.validateExecutableFile
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.runExecutableWithProgress
import com.jetbrains.python.sdk.uv.UvCli
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.exists
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

private fun <P : PathHolder> validateUvExecutable(uvPath: P?, platformAndRoot: PlatformAndRoot): ValidationInfo? {
  val path = when (uvPath) {
    is PathHolder.Eel -> uvPath.path.pathString
    is PathHolder.Target -> uvPath.pathString
  }
  return validateExecutableFile(ValidationRequest(
    path = path,
    fieldIsEmpty = PyBundle.message("python.sdk.uv.executable.not.found"),
    platformAndRoot = platformAndRoot
  ))
}

private suspend fun <P : PathHolder> runUv(uv: P, workingDir: Path, venvPath: P?, fileSystem: FileSystem<P>, canChangeTomlOrLock: Boolean, vararg args: String): PyResult<String> {
  val env = buildMap {
    if (venvPath == null) {
      put("VIRTUAL_ENV", VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME)
    }
    else {
      put("VIRTUAL_ENV", venvPath.toString())
    }
    venvPath?.let { put("UV_PROJECT_ENVIRONMENT", it.toString()) }
  }
  val bin = when (uv) {
    is PathHolder.Eel -> BinOnEel(uv.path, workingDir)
    is PathHolder.Target -> BinOnTarget(uv.pathString, (fileSystem as FileSystem.Target).targetEnvironmentConfiguration, workingDir)
  }
  val downloadConfig = if (canChangeTomlOrLock) DownloadConfig(relativePaths = listOf("pyproject.toml", "uv.lock")) else null
  return runExecutableWithProgress(bin, env = env, timeout = 10.minutes, args = args, transformer = ZeroCodeStdoutTransformer, downloadConfig = downloadConfig)
}

private class UvCliImpl<P : PathHolder>(val dispatcher: CoroutineDispatcher, val uv: P, private val fileSystem: FileSystem<P>) : UvCli<P> {

  override suspend fun runUv(workingDir: Path, venvPath: P?, canChangeTomlOrLock: Boolean, vararg args: String): PyResult<String> = withContext(dispatcher) {
    runUv(uv, workingDir, venvPath, fileSystem, canChangeTomlOrLock, *args)
  }
}

suspend fun <P : PathHolder> detectUvExecutable(fileSystem: FileSystem<P>, pathFromSdk: FullPathOnTarget?): P? = detectTool("uv", fileSystem, pathFromSdk)

private suspend fun <P : PathHolder> detectTool(
  toolName: String,
  fileSystem: FileSystem<P>,
  pathFromSdk: FullPathOnTarget?,
  additionalSearchPaths: List<P> = listOf(),
): P? = withContext(Dispatchers.IO) {
  pathFromSdk?.let { fileSystem.parsePath(it) }?.successOrNull?.also { return@withContext it }
  when (fileSystem) {
    is FileSystem.Eel -> fileSystem.which(toolName)
    is FileSystem.Target -> {
      val binary = fileSystem.which(toolName)
      if (binary != null) {
        return@withContext binary
      }

      val searchPaths: List<FullPathOnTarget> = buildList {
        fileSystem.getHomePath()?.also {
          add("$it/.local/bin/uv")
        }

        for (path in additionalSearchPaths) {
          add(path.toString())
        }
      }

      searchPaths.firstOrNull { fileSystem.fileExists(PathHolder.Target(it)) }
        ?.let { fileSystem.parsePath(it).successOrNull }
    }
  }
}

suspend fun getUvExecutableLocal(eel: EelApi = localEel): Path? {
  return PropertiesComponent.getInstance().uvPath?.takeIf { it.exists() } ?: detectUvExecutable(FileSystem.Eel(eel), null)?.path
}

// TODO PY-87712 check that path in SDK is from the same eel or something
suspend fun <P : PathHolder> getUvExecutable(fileSystem: FileSystem<P>, pathFromSdk: FullPathOnTarget?): P? {
  val pathFromProperty = PropertiesComponent.getInstance().uvPath
  return when (fileSystem) {
    is FileSystem.Eel -> (pathFromProperty?.takeIf { fileSystem.eelApi == localEel && it.exists() }?.let { PathHolder.Eel(it) as P }) ?: detectUvExecutable(fileSystem, pathFromSdk)
    is FileSystem.Target -> detectUvExecutable(fileSystem, pathFromSdk)
  }
}

fun setUvExecutableLocal(path: Path) {
  PropertiesComponent.getInstance().uvPath = path
}

suspend fun hasUvExecutableLocal(): Boolean {
  return getUvExecutableLocal() != null
}

suspend fun createUvCliLocal(uv: Path? = null, dispatcher: CoroutineDispatcher = Dispatchers.IO): PyResult<UvCli<PathHolder.Eel>> {
  return createUvCli(uv?.let { PathHolder.Eel(it) }, FileSystem.Eel(localEel), dispatcher)
}

suspend fun <P : PathHolder> createUvCli(uv: P?, fileSystem: FileSystem<P>, dispatcher: CoroutineDispatcher = Dispatchers.IO): PyResult<UvCli<P>> {
  val path = uv ?: getUvExecutable(fileSystem, null)
  val platformAndRoot = when (fileSystem) {
    is FileSystem.Eel -> fileSystem.eelApi.getPlatformAndRoot()
    is FileSystem.Target -> fileSystem.targetEnvironmentConfiguration.getPlatformAndRoot()
  }
  val error = validateUvExecutable(path, platformAndRoot)
  return if (error != null) {
    PyResult.localizedError(error.message)
  }
  else PyResult.success(UvCliImpl(dispatcher, path!!, fileSystem))
}