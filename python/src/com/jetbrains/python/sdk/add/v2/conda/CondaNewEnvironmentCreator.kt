// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.conda

import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.add.v2.*
import com.jetbrains.python.sdk.conda.condaSupportedLanguages
import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest
import com.jetbrains.python.statistics.InterpreterCreationMode
import com.jetbrains.python.statistics.InterpreterType
import com.jetbrains.python.ui.flow.bindText
import kotlinx.coroutines.CoroutineScope

internal class CondaNewEnvironmentCreator<P: PathHolder>(model: PythonMutableTargetAddInterpreterModel<P>) : PythonNewEnvironmentCreator<P>(model) {

  private lateinit var pythonVersion: ObservableMutableProperty<LanguageLevel>
  private lateinit var versionComboBox: ComboBox<LanguageLevel>
  private lateinit var condaExecutable: ValidatedPathField<Version, P, ValidatedPath.Executable<P>>

  override fun setupUI(panel: Panel, validationRequestor: DialogValidationRequestor) {
    with(panel) {
      row(message("sdk.create.python.version")) {
        pythonVersion = propertyGraph.property(condaSupportedLanguages.first())
        versionComboBox = comboBox(condaSupportedLanguages, textListCellRenderer { it?.toPythonVersion() })
          .bindItem(pythonVersion)
          .component
      }
      row(message("sdk.create.custom.conda.env.name")) {
        textField()
          .bindText(model.condaViewModel.newCondaEnvName) // property setter for getOrCreateSdk
          .bindText(model.projectPathFlows.projectName) // default value getter
      }

      condaExecutable = validatablePathField(
        fileSystem = model.fileSystem,
        pathValidator = model.condaViewModel.toolValidator,
        validationRequestor = validationRequestor,
        labelText = message("sdk.create.custom.venv.executable.path", "conda"),
        missingExecutableText = message("sdk.create.custom.venv.missing.text", "conda"),
        installAction = createInstallCondaFix(model)
      )
    }
  }

  override fun onShown(scope: CoroutineScope) {
    condaExecutable.initialize(scope)
    condaExecutable.displayLoaderWhen(
      loading = model.condaViewModel.condaEnvironmentsLoading,
      scope = scope,
    )
  }

  override suspend fun getOrCreateSdk(moduleOrProject: ModuleOrProject): PyResult<Sdk> {
    return model.createCondaEnvironment(moduleOrProject, NewCondaEnvRequest.EmptyNamedEnv(pythonVersion.get(), model.condaViewModel.newCondaEnvName.get()))
  }

  override fun createStatisticsInfo(target: PythonInterpreterCreationTargets): InterpreterStatisticsInfo {
    val statisticsTarget = target.toStatisticsField() // todo fix for wsl
    return InterpreterStatisticsInfo(
      type = InterpreterType.CONDAVENV,
      target = statisticsTarget,
      globalSitePackage = false,
      makeAvailableToAllProjects = false,
      previouslyConfigured = false,
      isWSLContext = false, // todo fix for wsl
      creationMode = InterpreterCreationMode.CUSTOM
    )
  }
}