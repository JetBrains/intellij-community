// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.PathUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.sdk.createSdk
import com.jetbrains.python.sdk.findAmongRoots
import com.jetbrains.python.sdk.setAssociationToModule
import com.jetbrains.python.sdk.uv.impl.createUvCli
import com.jetbrains.python.sdk.uv.impl.createUvLowLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.pathString

internal val Sdk.isUv: Boolean
  get() = sdkAdditionalData is UvSdkAdditionalData

internal suspend fun uvLock(module: com.intellij.openapi.module.Module): VirtualFile? {
  return withContext(Dispatchers.IO) {
    findAmongRoots(module, UV_LOCK)
  }
}

internal suspend fun pyProjectToml(module: Module): VirtualFile? {
  return withContext(Dispatchers.IO) {
    findAmongRoots(module, PY_PROJECT_TOML)
  }
}

internal fun suggestedSdkName(basePath: Path): @NlsSafe String {
  return "uv (${PathUtil.getFileName(basePath.pathString)})"
}

val UV_ICON = PythonIcons.UV
val UV_LOCK: String = "uv.lock"

// FIXME: move pyprojecttoml code out to common package
val PY_PROJECT_TOML: String = "pyproject.toml"

suspend fun setupUvSdkUnderProgress(
  module: Module,
  projectPath: Path,
  existingSdks: List<Sdk>,
  python: Path?
): Result<Sdk> {
  val uv = createUvLowLevel(projectPath, createUvCli())

  val init = pyProjectToml(module) == null
  val envExecutable =
    withBackgroundProgress(module.project, PyBundle.message("python.sdk.dialog.title.setting.up.uv.environment"), true) {
      uv.initializeEnvironment(init, python)
    }.getOrElse {
      return Result.failure(it)
    }

  val sdk = createSdk(envExecutable, existingSdks, projectPath.pathString, suggestedSdkName(projectPath), UvSdkAdditionalData())
  sdk.onSuccess {
    it.setAssociationToModule(module)
  }

  return sdk
}