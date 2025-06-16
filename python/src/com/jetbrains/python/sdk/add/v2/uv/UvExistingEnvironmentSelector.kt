// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.uv

import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.add.v2.CustomExistingEnvironmentSelector
import com.jetbrains.python.sdk.add.v2.DetectedSelectableInterpreter
import com.jetbrains.python.sdk.add.v2.PythonMutableTargetAddInterpreterModel
import com.jetbrains.python.sdk.associatedModulePath
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.isAssociatedWithModule
import com.jetbrains.python.sdk.uv.isUv
import com.jetbrains.python.sdk.uv.setupExistingEnvAndSdk
import com.jetbrains.python.statistics.InterpreterType
import com.jetbrains.python.statistics.version
import com.jetbrains.python.venvReader.tryResolvePath
import java.nio.file.Path
import kotlin.io.path.pathString

internal class UvExistingEnvironmentSelector(model: PythonMutableTargetAddInterpreterModel, module: Module?)
  : CustomExistingEnvironmentSelector("uv", model, module) {
  override val executable: ObservableMutableProperty<String> = model.state.uvExecutable
  override val interpreterType: InterpreterType = InterpreterType.UV

  override suspend fun getOrCreateSdk(moduleOrProject: ModuleOrProject): PyResult<Sdk> {
    val selectedInterpreterPath = tryResolvePath(selectedEnv.get()?.homePath) ?: return PyResult.localizedError("No selected interpreter")
    val allSdk = ProjectJdkTable.getInstance().allJdks
    val existingSdk = allSdk.find { it.homePath == selectedInterpreterPath.pathString }
    val associatedModule = extractModule(moduleOrProject)
    val projectDir = tryResolvePath(associatedModule?.basePath ?: moduleOrProject.project.basePath) ?: return PyResult.localizedError("No base path")

    // uv sdk in current module
    if (existingSdk != null && existingSdk.isUv && existingSdk.isAssociatedWithModule(associatedModule)) {
      return Result.success(existingSdk)
    }

    val existingWorkingDir = existingSdk?.associatedModulePath?.let { tryResolvePath(it) }
    val usePip = existingWorkingDir != null && !existingSdk.isUv

    return setupExistingEnvAndSdk(
      envExecutable = selectedInterpreterPath,
      envWorkingDir = existingWorkingDir,
      usePip = usePip,
      projectDir = projectDir,
      existingSdks = allSdk.toList()
    )
  }

  override suspend fun detectEnvironments(modulePath: Path): List<DetectedSelectableInterpreter> {
    val existingEnvs = ProjectJdkTable.getInstance().allJdks.filter {
      it.isUv && (it.associatedModulePath == modulePath.pathString || it.associatedModulePath == null)
    }.mapNotNull { env ->
      env.homePath?.let { path -> DetectedSelectableInterpreter(path, env.version, false) }
    }
    return existingEnvs
  }

  private fun extractModule(moduleOrProject: ModuleOrProject): Module? =
    when (moduleOrProject) {
      is ModuleOrProject.ModuleAndProject -> moduleOrProject.module
      else -> null
    }
}