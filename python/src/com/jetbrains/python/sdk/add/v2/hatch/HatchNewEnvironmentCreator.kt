// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.hatch

import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.python.hatch.HatchConfiguration
import com.intellij.python.hatch.getHatchService
import com.intellij.ui.dsl.builder.Panel
import com.intellij.util.text.nullize
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.onSuccess
import com.jetbrains.python.sdk.add.v2.CustomNewEnvironmentCreator
import com.jetbrains.python.sdk.add.v2.PythonMutableTargetAddInterpreterModel
import com.jetbrains.python.sdk.hatch.createSdk
import com.jetbrains.python.statistics.InterpreterType
import java.nio.file.Path

internal class HatchNewEnvironmentCreator(
  override val model: PythonMutableTargetAddInterpreterModel,
) : CustomNewEnvironmentCreator("hatch", model) {
  override val interpreterType: InterpreterType = InterpreterType.HATCH
  override val executable: ObservableMutableProperty<String> = propertyGraph.property(model.state.hatchExecutable.get())

  init {
    propertyGraph.dependsOn(executable, model.state.hatchExecutable, deleteWhenChildModified = false) {
      model.state.hatchExecutable.get()
    }
  }

  override val installationVersion: String? = null

  override fun buildOptions(panel: Panel, validationRequestor: DialogValidationRequestor, errorSink: ErrorSink) {
    panel.buildHatchFormFields(
      model = model,
      hatchExecutableProperty = executable,
      propertyGraph = propertyGraph,
      validationRequestor = validationRequestor,
      isGenerateNewMode = true,
      installHatchActionLink = createInstallFix(errorSink)
    ) {
      basePythonComboBox = it
    }
  }

  override fun savePathToExecutableToProperties(path: Path?) {
    val savingPath = path ?: executable.get().nullize()?.let { Path.of(it) } ?: return
    HatchConfiguration.persistPathForTarget(hatchExecutablePath = savingPath)
  }

  override suspend fun setupEnvSdk(project: Project?, module: Module?, baseSdks: List<Sdk>, projectPath: String, homePath: String?, installPackages: Boolean): Result<Sdk, PyError> {
    module ?: return Result.failure(HatchUIError.ModuleIsNotSelected())
    val selectedEnv = model.state.selectedHatchEnv.get() ?: return Result.failure(HatchUIError.HatchEnvironmentIsNotSelected())

    val hatchExecutablePath = executable.get().toPath().getOr { return it }
    val hatchService = module.getHatchService(hatchExecutablePath = hatchExecutablePath).getOr { return it }

    val existingHatchVenv = hatchService.createVirtualEnvironment(
      basePythonBinaryPath = homePath?.let { Path.of(it) },
      envName = selectedEnv.hatchEnvironment.name
    ).getOr { return it }

    val createdSdk = existingHatchVenv.createSdk(module).onSuccess {
      HatchConfiguration.persistPathForTarget(hatchExecutablePath = hatchExecutablePath)
    }
    return createdSdk
  }

  override suspend fun detectExecutable() {
    model.detectHatchExecutable()
  }
}
