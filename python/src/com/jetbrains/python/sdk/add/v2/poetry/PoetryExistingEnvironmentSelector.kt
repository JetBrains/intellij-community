// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.poetry

import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PyBundle
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.add.v2.CustomExistingEnvironmentSelector
import com.jetbrains.python.sdk.add.v2.DetectedSelectableInterpreter
import com.jetbrains.python.sdk.add.v2.PythonMutableTargetAddInterpreterModel
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.poetry.createPoetrySdk
import com.jetbrains.python.sdk.poetry.detectPoetryEnvs
import com.jetbrains.python.sdk.poetry.isPoetry
import com.jetbrains.python.statistics.InterpreterType
import com.jetbrains.python.statistics.version
import java.nio.file.Path
import kotlin.io.path.pathString

internal class PoetryExistingEnvironmentSelector(model: PythonMutableTargetAddInterpreterModel, module: Module?) : CustomExistingEnvironmentSelector("poetry", model, module) {
  override val executable: ObservableMutableProperty<String> = model.state.poetryExecutable
  override val interpreterType: InterpreterType = InterpreterType.POETRY

  override suspend fun getOrCreateSdk(moduleOrProject: ModuleOrProject): PyResult<Sdk> {
    val pythonBinaryPath = selectedEnv.get()?.let { Path.of(it.homePath) }
                           ?: return PyResult.localizedError(PyBundle.message("python.sdk.provided.path.is.invalid", selectedEnv.get()?.homePath))

    PythonSdkUtil.getAllSdks().find { sdk -> sdk.isPoetry && sdk.homePath == pythonBinaryPath.pathString }?.let { return Result.success(it) }

    val module = when (moduleOrProject) {
      is ModuleOrProject.ModuleAndProject -> {
        moduleOrProject.module
      }
      else -> null
    }
    val moduleBasePath = module?.basePath?.let { Path.of(it) } ?: error("module base path is not valid: ${module?.basePath}")

    return createPoetrySdk(
      moduleBasePath,
      existingSdks = ProjectJdkTable.getInstance().allJdks.toList(),
      pythonBinaryPath = pythonBinaryPath
    )
  }

  override suspend fun detectEnvironments(modulePath: Path): List<DetectedSelectableInterpreter> {
    val existingEnvs = detectPoetryEnvs(null, null, modulePath.pathString).mapNotNull { env ->
      env.homePath?.let { path -> DetectedSelectableInterpreter(path, env.version, false) }
    }
    return existingEnvs
  }
}