// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.PathUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.uv.impl.createUvCli
import com.jetbrains.python.sdk.uv.impl.createUvLowLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import javax.swing.Icon
import kotlin.io.path.pathString

internal val Sdk.isUv: Boolean
  get() = sdkAdditionalData is UvSdkAdditionalData

internal suspend fun pyProjectToml(module: Module): VirtualFile? {
  return withContext(Dispatchers.IO) {
    findAmongRoots(module, PY_PROJECT_TOML)
  }
}

internal fun suggestedSdkName(basePath: Path): @NlsSafe String {
  return "uv (${PathUtil.getFileName(basePath.pathString)})"
}

val UV_ICON: Icon = PythonIcons.UV

// FIXME: move pyprojecttoml code out to common package
const val PY_PROJECT_TOML: String = "pyproject.toml"

suspend fun setupUvSdkUnderProgress(
  moduleOrProject: ModuleOrProject,
  existingSdks: List<Sdk>,
  python: Path?,
  existingSdkWorkingDir: Path? = null,
  usePip: Boolean = false,
): Result<Sdk> {

  val (pyProjectToml, moduleWorkingDirectory) = resolveWorkingDirectory(moduleOrProject)
  val init = pyProjectToml == null
  val uvWorkingDir = existingSdkWorkingDir ?: moduleWorkingDirectory
  val uv = createUvLowLevel(uvWorkingDir, createUvCli())

  val envExecutable =
    if (existingSdkWorkingDir == null) {
      withBackgroundProgress(moduleOrProject.project, PyBundle.message("python.sdk.dialog.title.setting.up.uv.environment"), true) {
        uv.initializeEnvironment(init, python)
      }.getOrElse {
        return Result.failure(it)
      }
    }
    else {
      python
    } ?: throw IllegalArgumentException("Python executable is required to setup uv environment")

  val sdk = createSdk(envExecutable, existingSdks, moduleWorkingDirectory.pathString, suggestedSdkName(moduleWorkingDirectory), UvSdkAdditionalData(existingSdkWorkingDir, usePip))
  sdk.onSuccess {
    it.setAssociationToPath(moduleWorkingDirectory.pathString)
  }

  return sdk
}

private suspend fun resolveWorkingDirectory(moduleOrProject: ModuleOrProject): Pair<VirtualFile?, Path> {
  var pyProjectToml: VirtualFile? = null
  val workingDirectory = when (moduleOrProject) {
                           is ModuleOrProject.ModuleAndProject -> {
                             pyProjectToml = pyProjectToml(moduleOrProject.module)
                             pyProjectToml?.toNioPathOrNull()?.parent ?: moduleOrProject.module.basePath?.let { Path.of(it) }
                           }
                           else -> moduleOrProject.project.basePath?.let { Path.of(it) }
                         } ?: throw IllegalArgumentException("Path to module or working directory is required")

  return Pair(pyProjectToml, workingDirectory)
}