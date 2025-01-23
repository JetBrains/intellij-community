// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.toNioPathOrNull
import com.jetbrains.python.Result
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.poetry.detectPoetryEnvs
import com.jetbrains.python.sdk.poetry.isPoetry
import com.jetbrains.python.sdk.poetry.pyProjectToml
import com.jetbrains.python.sdk.poetry.setupPoetrySdkUnderProgress
import com.jetbrains.python.statistics.InterpreterType
import com.jetbrains.python.statistics.version
import com.jetbrains.python.util.PyError
import com.jetbrains.python.util.asPythonResult
import java.nio.file.Path
import kotlin.io.path.pathString

internal class PoetryExistingEnvironmentSelector(model: PythonMutableTargetAddInterpreterModel, moduleOrProject: ModuleOrProject) : CustomExistingEnvironmentSelector("poetry", model, moduleOrProject) {
  override val executable: ObservableMutableProperty<String> = model.state.poetryExecutable
  override val interpreterType: InterpreterType = InterpreterType.POETRY

  override suspend fun getOrCreateSdk(moduleOrProject: ModuleOrProject): com.jetbrains.python.Result<Sdk, PyError> {
    val selectedInterpreter = selectedEnv.get()
    ProjectJdkTable.getInstance().allJdks.find { sdk -> sdk.isPoetry && sdk.homePath == selectedInterpreter?.homePath }?.let { return Result.success(it) }
    val module = when (moduleOrProject) {
      is ModuleOrProject.ModuleAndProject -> {
        moduleOrProject.module
      }
      else -> null
    }

    return setupPoetrySdkUnderProgress(moduleOrProject.project, module, ProjectJdkTable.getInstance().allJdks.toList(), null, selectedInterpreter?.homePath, true).asPythonResult()
  }

  override suspend fun detectEnvironments(modulePath: Path) {
    val existingEnvs = detectPoetryEnvs(null, null, modulePath.pathString).mapNotNull {
      env -> env.homePath?.let { path -> DetectedSelectableInterpreter(path, env.version) }
    }

    existingEnvironments.value = existingEnvs
  }

  override suspend fun findModulePath(module: Module): Path? = pyProjectToml(module)?.toNioPathOrNull()?.parent
}