// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.hatch

import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.python.hatch.HatchConfiguration
import com.intellij.python.hatch.HatchVirtualEnvironment
import com.intellij.python.hatch.getHatchService
import com.intellij.ui.dsl.builder.Panel
import com.intellij.util.text.nullize
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.hatch.sdk.createSdk
import com.jetbrains.python.onSuccess
import com.jetbrains.python.sdk.add.v2.CustomNewEnvironmentCreator
import com.jetbrains.python.sdk.add.v2.PythonMutableTargetAddInterpreterModel
import com.jetbrains.python.statistics.InterpreterType
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path

internal class HatchNewEnvironmentCreator(
  override val model: PythonMutableTargetAddInterpreterModel,
  errorSink: ErrorSink,
) : CustomNewEnvironmentCreator("hatch", model, errorSink) {
  override val interpreterType: InterpreterType = InterpreterType.HATCH
  override val executable: ObservableMutableProperty<String> = propertyGraph.property(model.state.hatchExecutable.get())
  private val hatchEnvironmentProperty: ObservableMutableProperty<HatchVirtualEnvironment?> = propertyGraph.property(null)
  private lateinit var hatchFormFields: HatchFormFields

  init {
    propertyGraph.dependsOn(executable, model.state.hatchExecutable, deleteWhenChildModified = false) {
      model.state.hatchExecutable.get()
    }
  }

  override fun setupUI(panel: Panel, validationRequestor: DialogValidationRequestor) {
    hatchFormFields = panel.buildHatchFormFields(
      model = model,
      hatchExecutableProperty = executable,
      hatchEnvironmentProperty = hatchEnvironmentProperty,
      propertyGraph = propertyGraph,
      validationRequestor = validationRequestor,
      isGenerateNewMode = true,
      installHatchActionLink = createInstallFix(errorSink)
    )
    basePythonComboBox = requireNotNull(hatchFormFields.basePythonComboBox)
  }

  override fun onShown(scope: CoroutineScope) {
    super.onShown(scope)
    hatchFormFields.onShown(scope, model, state, isFilterOnlyExisting = false)
  }

  override fun savePathToExecutableToProperties(path: Path?) {
    val savingPath = path ?: executable.get().nullize()?.let { Path.of(it) } ?: return
    HatchConfiguration.persistPathForTarget(hatchExecutablePath = savingPath)
  }

  override suspend fun createPythonModuleStructure(module: Module): PyResult<Unit> {
    val hatchExecutablePath = executable.get().toPath().getOr { return it }
    val hatchService = module.getHatchService(hatchExecutablePath = hatchExecutablePath).getOr { return it }

    val projectStructure = hatchService.createNewProject(module.project.name).getOr { return it }
    ModuleRootModificationUtil.updateModel(module) { moduleRootModel ->
      val contentEntry = moduleRootModel.contentEntries.firstOrNull() ?: return@updateModel

      projectStructure.sourceRoot?.let { VfsUtilCore.pathToUrl(it.toString()) }?.let { sourceRootUrl ->
        contentEntry.addSourceFolder(sourceRootUrl, false)
      }
      projectStructure.testRoot?.let { VfsUtilCore.pathToUrl(it.toString()) }?.let { testRootUrl ->
        contentEntry.addSourceFolder(testRootUrl, true)
      }
    }
    return Result.success(Unit)
  }

  override suspend fun setupEnvSdk(moduleBasePath: Path, baseSdks: List<Sdk>, basePythonBinaryPath: PythonBinary?, installPackages: Boolean): PyResult<Sdk> {
    val hatchEnv = hatchEnvironmentProperty.get()?.hatchEnvironment
                   ?: return Result.failure(HatchUIError.HatchEnvironmentIsNotSelected())

    val hatchExecutablePath = executable.get().toPath().getOr { return it }
    val hatchService = moduleBasePath.getHatchService(hatchExecutablePath = hatchExecutablePath).getOr { return it }

    val virtualEnvironment = hatchService.createVirtualEnvironment(
      basePythonBinaryPath = basePythonBinaryPath,
      envName = hatchEnv.name
    ).getOr { return it }

    val hatchVirtualEnv = HatchVirtualEnvironment(hatchEnv, virtualEnvironment)
    val createdSdk = hatchVirtualEnv.createSdk(hatchService.getWorkingDirectoryPath()).onSuccess {
      HatchConfiguration.persistPathForTarget(hatchExecutablePath = hatchExecutablePath)
    }
    return createdSdk
  }

  override suspend fun detectExecutable() {
    model.detectHatchExecutable()
  }
}
