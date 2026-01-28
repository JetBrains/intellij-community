// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.poetry

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.impl.poetry.common.POETRY_UI_INFO
import com.intellij.python.community.impl.poetry.common.poetryPath
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonInfo
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.add.v2.*
import com.jetbrains.python.sdk.baseDir
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.moduleIfExists
import com.jetbrains.python.sdk.poetry.createPoetrySdk
import com.jetbrains.python.sdk.poetry.detectPoetryEnvs
import com.jetbrains.python.sdk.poetry.isPoetry
import com.jetbrains.python.statistics.InterpreterType
import com.jetbrains.python.statistics.version
import java.nio.file.Path
import kotlin.io.path.pathString

internal class PoetryExistingEnvironmentSelector<P : PathHolder>(model: PythonMutableTargetAddInterpreterModel<P>, module: Module?) :
  CustomExistingEnvironmentSelector<P>("poetry", model, module) {
  override val interpreterType: InterpreterType = InterpreterType.POETRY
  override val toolState: ToolValidator<P> = model.poetryViewModel.toolValidator
  override val toolExecutable: ObservableProperty<ValidatedPath.Executable<P>?> = model.poetryViewModel.poetryExecutable
  override val toolExecutablePersister: suspend (P) -> Unit = { pathHolder ->
    savePathForEelOnly(pathHolder) { path -> PropertiesComponent.getInstance().poetryPath = path.toString() }
  }

  override suspend fun getOrCreateSdk(moduleOrProject: ModuleOrProject): PyResult<Sdk> {

    val pythonBinaryPath = selectedEnv.get()?.homePath as? PathHolder.Eel
                           ?: return PyResult.localizedError(PyBundle.message("python.sdk.provided.path.is.invalid",
                                                                              selectedEnv.get()?.homePath))

    PythonSdkUtil.getAllSdks().find { sdk -> sdk.isPoetry && sdk.homePath == pythonBinaryPath.toString() }?.let {
      return Result.success(it)
    }

    val basePathString = moduleOrProject.moduleIfExists?.baseDir?.path ?: moduleOrProject.project.basePath
    val basePath = basePathString?.let { Path.of(it) } ?: error("module base path is not valid: $basePathString")

    return createPoetrySdk(basePath, pythonBinaryPath)
  }

  override suspend fun detectEnvironments(modulePath: Path): List<DetectedSelectableInterpreter<P>> {
    val existingEnvs = detectPoetryEnvs(null, null, modulePath.pathString).mapNotNull { env ->
      env.homePath?.let { path ->
        model.fileSystem.parsePath(path).successOrNull?.let { fsPath ->
          DetectedSelectableInterpreter(fsPath, PythonInfo(env.version), false, POETRY_UI_INFO)
        }
      }
    }
    return existingEnvs
  }
}
