// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.poetry

import com.jetbrains.python.sdk.add.v2.addInterpreterByBinary
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.python.community.execService.python.validatePythonAndGetInfo
import com.intellij.python.community.impl.poetry.backend.PoetryPyTool
import com.intellij.python.community.impl.poetry.common.POETRY_UI_INFO
import com.intellij.python.sdk.backend.PythonInterpreter
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.orLogException
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.add.v2.CustomExistingEnvironmentSelector
import com.jetbrains.python.sdk.add.v2.DetectedSelectableInterpreter
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.PythonMutableTargetAddInterpreterModel
import com.jetbrains.python.sdk.add.v2.ToolValidator
import com.jetbrains.python.sdk.add.v2.ValidatedPath
import com.jetbrains.python.sdk.add.v2.pathHolder
import com.jetbrains.python.sdk.poetry.createPoetrySdk
import com.jetbrains.python.sdk.poetry.detectPoetryEnvironments
import com.jetbrains.python.sdk.workingDirectory
import com.jetbrains.python.statistics.InterpreterType
import java.nio.file.Path

internal class PoetryExistingEnvironmentSelector<P : PathHolder>(model: PythonMutableTargetAddInterpreterModel<P>, module: Module?) :
  CustomExistingEnvironmentSelector<P>("poetry", model, module) {
  override val interpreterType: InterpreterType = InterpreterType.POETRY
  override val toolState: ToolValidator<P> = model.poetryViewModel.toolValidator
  override val toolExecutable: ObservableProperty<ValidatedPath.Executable<P>?> = model.poetryViewModel.poetryExecutable
  override val toolExecutablePersister: suspend (P) -> Unit = { pathHolder ->
    model.fileSystem.persistCustomToolPath(pathHolder, PoetryPyTool.getInstance())
  }

  override suspend fun getOrCreateSdk(moduleOrProject: ModuleOrProject): PyResult<PythonInterpreter> {

    val pythonBinaryPath =
      selectedEnv.get()?.homePath ?: return PyResult.localizedError(PyBundle.message("python.sdk.provided.path.is.invalid",
                                                                                     selectedEnv.get()?.homePath))

    // The poetry node names the env by where it is, so the dialog asks it for the env ref of this binary.
    (pythonBinaryPath as? PathHolder.Eel)?.let { model.fileSystem.addInterpreterByBinary(moduleOrProject, PoetryPyTool.getInstance(), it.path) }
      ?.let { return it }

    val basePath =
      moduleOrProject.workingDirectory ?: return PyResult.localizedError(PyBundle.message("python.sdk.project.working.directory.not.found"))

    return createPoetrySdk(
      moduleOrProject = moduleOrProject,
      basePath = basePath,
      pythonBinaryPath = pythonBinaryPath,
      fileSystem = model.fileSystem,
      targetPanelExtension = model.state.targetPanelExtension.get(),
    )
  }

  override suspend fun detectEnvironments(modulePath: Path): List<DetectedSelectableInterpreter<P>> {
    val poetryExecutable = model.poetryViewModel.poetryExecutable.get()?.pathHolder?.successOrNull
    // The selector has no place for an error, so a failure gives an empty list and goes to the log.
    val pythonBinaries = detectPoetryEnvironments(modulePath, model.fileSystem, poetryExecutable).orLogException(LOG) ?: return emptyList()
    return pythonBinaries.mapNotNull { pythonBinary ->
      val pythonInfo = model.fileSystem.getBinaryToExec(pythonBinary).validatePythonAndGetInfo().successOrNull ?: return@mapNotNull null
      DetectedSelectableInterpreter(pythonBinary, pythonInfo, false, POETRY_UI_INFO)
    }
  }
}

private val LOG = fileLogger()
