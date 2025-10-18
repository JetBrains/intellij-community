// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.hatch

import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.hatch.HatchConfiguration
import com.intellij.python.hatch.PythonVirtualEnvironment
import com.intellij.python.hatch.resolveHatchWorkingDirectory
import com.intellij.ui.dsl.builder.Panel
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.hatch.sdk.createSdk
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.onSuccess
import com.jetbrains.python.sdk.impl.resolvePythonBinary
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.add.v2.*
import com.jetbrains.python.sdk.destructured
import com.jetbrains.python.sdk.setAssociationToModule
import com.jetbrains.python.statistics.InterpreterCreationMode
import com.jetbrains.python.statistics.InterpreterType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class HatchExistingEnvironmentSelector<P: PathHolder>(
  override val model: PythonMutableTargetAddInterpreterModel<P>,
) : PythonExistingEnvironmentConfigurator<P>(model) {
  val interpreterType: InterpreterType = InterpreterType.HATCH
  val executable: ObservableMutableProperty<ValidatedPath.Executable<P>?> = model.state.hatchExecutable

  private lateinit var hatchFormFields: HatchFormFields<P>

  override fun setupUI(panel: Panel, validationRequestor: DialogValidationRequestor) {
    hatchFormFields = panel.buildHatchFormFields(
      model = model,
      hatchEnvironmentProperty = state.selectedHatchEnv,
      hatchExecutableProperty = executable,
      validationRequestor = validationRequestor,
      isGenerateNewMode = false,
    )
  }

  override fun onShown(scope: CoroutineScope) {
    hatchFormFields.onShown(scope, model, state, isFilterOnlyExisting = true)
    executable.afterChange { hatchExecutable ->
      if (hatchExecutable?.validationResult?.successOrNull == null) {
        model.hatchEnvironmentsResult.value = null
        return@afterChange
      }

      val binaryToExec = hatchExecutable.pathHolder?.let { model.fileSystem.getBinaryToExec(it) }
                         ?: return@afterChange
      scope.launch(Dispatchers.IO) {
        model.detectHatchEnvironments(binaryToExec).also {
          model.hatchEnvironmentsResult.value = it
        }
      }
    }
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
        environment.createSdk(workingDirectory).onSuccess { sdk ->
          module?.let { module -> sdk.setAssociationToModule(module) }
        }
      }
    }.onSuccess {
      when (val binaryToExec = executable.get()?.pathHolder) {
        is BinOnEel -> HatchConfiguration.persistPathForTarget(hatchExecutablePath = binaryToExec.path)
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