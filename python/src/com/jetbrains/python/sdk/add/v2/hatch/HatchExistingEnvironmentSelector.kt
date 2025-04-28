// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.hatch

import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.python.hatch.HatchConfiguration
import com.intellij.python.hatch.PythonVirtualEnvironment
import com.intellij.python.hatch.resolveHatchWorkingDirectory
import com.intellij.ui.dsl.builder.Panel
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.hatch.sdk.createSdk
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.onSuccess
import com.jetbrains.python.resolvePythonBinary
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.add.v2.PythonExistingEnvironmentConfigurator
import com.jetbrains.python.sdk.add.v2.PythonInterpreterCreationTargets
import com.jetbrains.python.sdk.add.v2.PythonMutableTargetAddInterpreterModel
import com.jetbrains.python.sdk.add.v2.toStatisticsField
import com.jetbrains.python.sdk.destructured
import com.jetbrains.python.sdk.setAssociationToModuleAsync
import com.jetbrains.python.statistics.InterpreterCreationMode
import com.jetbrains.python.statistics.InterpreterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class HatchExistingEnvironmentSelector(
  override val model: PythonMutableTargetAddInterpreterModel,
  val moduleOrProject: ModuleOrProject,
) : PythonExistingEnvironmentConfigurator(model) {
  val interpreterType: InterpreterType = InterpreterType.HATCH
  val executable: ObservableMutableProperty<String> = propertyGraph.property(model.state.hatchExecutable.get())

  init {
    propertyGraph.dependsOn(executable, model.state.hatchExecutable, deleteWhenChildModified = false) {
      model.state.hatchExecutable.get()
    }
  }

  override fun buildOptions(panel: Panel, validationRequestor: DialogValidationRequestor, errorSink: ErrorSink) {
    panel.buildHatchFormFields(
      model = model,
      hatchEnvironmentProperty = state.selectedHatchEnv,
      hatchExecutableProperty = executable,
      propertyGraph = propertyGraph,
      validationRequestor = validationRequestor,
      isGenerateNewMode = false,
    )
  }

  override suspend fun getOrCreateSdk(moduleOrProject: ModuleOrProject): PyResult<Sdk> {
    val environment = state.selectedHatchEnv.get()
    val existingHatchVenv = environment?.pythonVirtualEnvironment as? PythonVirtualEnvironment.Existing
                            ?: return Result.failure(HatchUIError.HatchEnvironmentIsNotSelected())

    val venvPythonBinaryPathString = withContext(Dispatchers.IO) {
      existingHatchVenv.pythonHomePath.resolvePythonBinary().toString()
    }

    val existingSdk = PythonSdkUtil.getAllSdks().find { it.homePath == venvPythonBinaryPathString }
    val result = when {
      existingSdk != null -> Result.success(existingSdk)
      else -> {
        val (project, module) = moduleOrProject.destructured
        val workingDirectory = resolveHatchWorkingDirectory(project, module).getOr { return it }
        environment.createSdk(workingDirectory, module).onSuccess { sdk ->
          module?.let { module -> sdk.setAssociationToModuleAsync(module) }
        }
      }
    }.onSuccess {
      val executablePath = executable.get().toPath().getOr { return@onSuccess }
      HatchConfiguration.persistPathForTarget(hatchExecutablePath = executablePath)
    }

    return result
  }

  override fun createStatisticsInfo(target: PythonInterpreterCreationTargets): InterpreterStatisticsInfo {
    val statisticsTarget = target.toStatisticsField()
    return InterpreterStatisticsInfo(
      type = InterpreterType.HATCH,
      target = statisticsTarget,
      globalSitePackage = false,
      makeAvailableToAllProjects = false,
      previouslyConfigured = true,
      isWSLContext = false,
      creationMode = InterpreterCreationMode.CUSTOM
    )
  }
}