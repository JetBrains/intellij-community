// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.toNioPathOrNull
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.associatedModulePath
import com.jetbrains.python.sdk.isAssociatedWithModule
import com.jetbrains.python.sdk.uv.isUv
import com.jetbrains.python.sdk.uv.pyProjectToml
import com.jetbrains.python.sdk.uv.setupUvSdkUnderProgress
import com.jetbrains.python.statistics.InterpreterType
import com.jetbrains.python.statistics.version
import com.jetbrains.python.util.PyError
import com.jetbrains.python.util.asPythonResult
import com.jetbrains.python.util.failure
import java.nio.file.Path
import kotlin.io.path.pathString

internal class UvExistingEnvironmentSelector(model: PythonMutableTargetAddInterpreterModel, moduleOrProject: ModuleOrProject)
  : CustomExistingEnvironmentSelector("uv", model, moduleOrProject) {
  override val executable: ObservableMutableProperty<String> = model.state.uvExecutable
  override val interpreterType: InterpreterType = InterpreterType.UV

  override suspend fun getOrCreateSdk(moduleOrProject: ModuleOrProject): com.jetbrains.python.Result<Sdk, PyError> {
    val selectedInterpreterPath = selectedEnv.get()?.homePath ?: return failure("No selected interpreter")
    val existingSdk = ProjectJdkTable.getInstance().allJdks.find { it.homePath == selectedInterpreterPath }
    val associatedModule = extractModule(moduleOrProject)

    // uv sdk in current module
    if (existingSdk != null && existingSdk.isUv && existingSdk.isAssociatedWithModule(associatedModule)) {
      return com.jetbrains.python.Result.success(existingSdk)
    }

    val existingWorkingDir = existingSdk?.associatedModulePath?.let { Path.of(it) }
    val usePip = existingWorkingDir!= null && !existingSdk.isUv

    return setupUvSdkUnderProgress(moduleOrProject, ProjectJdkTable.getInstance().allJdks.toList(), Path.of(selectedInterpreterPath), existingWorkingDir, usePip).asPythonResult()
  }

  override suspend fun detectEnvironments(modulePath: Path) {
    val existingEnvs = ProjectJdkTable.getInstance().allJdks.filter {
      it.isUv && (it.associatedModulePath == modulePath.pathString || it.associatedModulePath == null)
    }.mapNotNull { env ->
      env.homePath?.let { path -> DetectedSelectableInterpreter(path, env.version) }
    }

    existingEnvironments.value = existingEnvs
  }

  override suspend fun findModulePath(module: Module): Path? = pyProjectToml(module)?.toNioPathOrNull()?.parent

  private fun extractModule(moduleOrProject: ModuleOrProject): Module? =
    when (moduleOrProject) {
      is ModuleOrProject.ModuleAndProject -> moduleOrProject.module
      else -> null
    }
}