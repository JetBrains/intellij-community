// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.hatch

import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.python.hatch.HatchConfiguration
import com.intellij.python.hatch.PythonVirtualEnvironment
import com.intellij.python.hatch.resolveHatchWorkingDirectory
import com.intellij.ui.dsl.builder.Panel
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.hatch.sdk.createSdk
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.onSuccess
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.add.v2.*
import com.jetbrains.python.sdk.destructured
import com.jetbrains.python.sdk.impl.resolvePythonBinary
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.statistics.InterpreterCreationMode
import com.jetbrains.python.statistics.InterpreterType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class HatchExistingEnvironmentSelector<P : PathHolder>(
  override val model: PythonMutableTargetAddInterpreterModel<P>,
) : PythonExistingEnvironmentConfigurator<P>(model) {
  val interpreterType: InterpreterType = InterpreterType.HATCH

  private lateinit var hatchFormFields: HatchFormFields<P>
  override val toolExecutable: ObservableProperty<ValidatedPath.Executable<P>?> = model.hatchViewModel.hatchExecutable
  override val toolExecutablePersister: suspend (P) -> Unit = { pathHolder ->
    savePathForEelOnly(pathHolder) { path -> HatchConfiguration.persistPathForTarget(hatchExecutablePath = path) }
  }

  override fun setupUI(panel: Panel, validationRequestor: DialogValidationRequestor) {
    hatchFormFields = panel.buildHatchFormFields(
      model = model,
      validationRequestor = validationRequestor,
      isGenerateNewMode = false,
    )
  }

  override fun onShown(scope: CoroutineScope) {
    hatchFormFields.onShown(scope, model, isFilterOnlyExisting = true)
  }

  override suspend fun getOrCreateSdk(moduleOrProject: ModuleOrProject): PyResult<Sdk> {
    val environment = model.hatchViewModel.selectedEnvFromExisting.get()
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
        environment.createSdk(workingDirectory)
      }
    }.onSuccess {
      when (val pathHolder = model.hatchViewModel.hatchExecutable.get()?.pathHolder) {
        is PathHolder.Eel -> HatchConfiguration.persistPathForTarget(hatchExecutablePath = pathHolder.path)
        else -> Unit
      }
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