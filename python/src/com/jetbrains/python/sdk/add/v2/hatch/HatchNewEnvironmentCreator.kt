// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.hatch

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.python.hatch.HatchConfiguration
import com.intellij.python.hatch.HatchVirtualEnvironment
import com.intellij.python.hatch.getHatchService
import com.intellij.ui.dsl.builder.Panel
import com.jetbrains.python.PyBundle
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.hatch.sdk.createSdk
import com.jetbrains.python.onSuccess
import com.jetbrains.python.sdk.add.v2.*
import com.jetbrains.python.statistics.InterpreterType
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path

internal class HatchNewEnvironmentCreator<P : PathHolder>(
  override val model: PythonMutableTargetAddInterpreterModel<P>,
  errorSink: ErrorSink,
) : CustomNewEnvironmentCreator<P>("hatch", model, errorSink) {
  override val interpreterType: InterpreterType = InterpreterType.HATCH
  override val toolValidator: ToolValidator<P> = model.hatchViewModel.toolValidator
  private lateinit var hatchFormFields: HatchFormFields<P>

  override fun setupUI(panel: Panel, validationRequestor: DialogValidationRequestor) {
    hatchFormFields = panel.buildHatchFormFields(
      model = model,
      validationRequestor = validationRequestor,
      isGenerateNewMode = true,
      installHatchActionLink = createInstallFix(errorSink)
    )
    basePythonComboBox = requireNotNull(hatchFormFields.basePythonComboBox)
    executablePath = requireNotNull(hatchFormFields.validatedPathField)
  }

  override fun onShown(scope: CoroutineScope) {
    super.onShown(scope)
    hatchFormFields.onShown(scope, model, isFilterOnlyExisting = false)
  }

  override suspend fun savePathToExecutableToProperties(pathHolder: PathHolder?) {
    val savingPath = pathHolder ?: toolValidator.backProperty.get()?.pathHolder ?: return
    val eelPath = (savingPath as? PathHolder.Eel)?.path ?: return
    HatchConfiguration.persistPathForTarget(hatchExecutablePath = eelPath)
  }

  override suspend fun createPythonModuleStructure(module: Module): PyResult<Unit> {
    val hatchExecutablePath = (toolValidator.backProperty.get()?.pathHolder as? PathHolder.Eel)?.path
                              ?: return Result.failure(HatchUIError.HatchExecutablePathIsNotValid(null))

    val hatchService = module.getHatchService(hatchExecutablePath).getOr { return it }

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

  override suspend fun setupEnvSdk(moduleBasePath: Path, baseSdks: List<Sdk>, basePythonBinaryPath: P?, installPackages: Boolean): PyResult<Sdk> {
    val hatchEnv = model.hatchViewModel.selectedEnvFromAvailable.get()?.hatchEnvironment
                   ?: return Result.failure(HatchUIError.HatchEnvironmentIsNotSelected())
    val basePythonBinaryEelPath = when (basePythonBinaryPath) {
      is PathHolder.Eel -> basePythonBinaryPath.path
      else -> return PyResult.localizedError(PyBundle.message("target.is.not.supported", basePythonBinaryPath))
    }
    val hatchExecutablePath = when (val hatchBinary = toolValidator.backProperty.get()?.pathHolder) {
      is PathHolder.Eel -> hatchBinary.path
      else -> null
    }
    val hatchService = moduleBasePath.getHatchService(hatchExecutablePath = hatchExecutablePath).getOr { return it }

    val virtualEnvironment = hatchService.createVirtualEnvironment(
      basePythonBinaryPath = basePythonBinaryEelPath,
      envName = hatchEnv.name
    ).getOr { return it }

    val hatchVirtualEnv = HatchVirtualEnvironment(hatchEnv, virtualEnvironment)
    val createdSdk = hatchVirtualEnv.createSdk(hatchService.getWorkingDirectoryPath()).onSuccess {
      HatchConfiguration.persistPathForTarget(hatchExecutablePath = hatchExecutablePath)
    }
    return createdSdk
  }
}
