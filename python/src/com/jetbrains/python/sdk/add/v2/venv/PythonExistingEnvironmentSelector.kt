// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.venv

import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.ui.dsl.builder.Panel
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.add.v2.*
import com.jetbrains.python.statistics.InterpreterCreationMode
import com.jetbrains.python.statistics.InterpreterType
import kotlinx.coroutines.CoroutineScope

class PythonExistingEnvironmentSelector<P : PathHolder>(model: PythonAddInterpreterModel<P>, private val module: Module?) :
  PythonExistingEnvironmentConfigurator<P>(model) {
  private lateinit var comboBox: PythonInterpreterComboBox<P>
  override val toolExecutable: ObservableProperty<ValidatedPath.Executable<P>?>? = null
  override val toolExecutablePersister: suspend (P) -> Unit = { }

  override fun setupUI(panel: Panel, validationRequestor: DialogValidationRequestor) {
    with(panel) {
      comboBox = pythonInterpreterComboBox(
        model.fileSystem,
        title = message("sdk.create.custom.python.path"),
        selectedSdkProperty = model.state.selectedInterpreter,
        validationRequestor = validationRequestor,
        onPathSelected = model::addManuallyAddedPythonNotNecessarilySystem,
      )
    }
  }

  override fun onShown(scope: CoroutineScope) {
    comboBox.initialize(scope, model.allInterpreters.mapDistinctSortedForExistingEnvironment(module))
  }

  override suspend fun getOrCreateSdk(moduleOrProject: ModuleOrProject): PyResult<Sdk> {
    // todo error handling, nullability issues
    val sdk = model.state.selectedInterpreter.get()!!.setupSdk(
      moduleOrProject = moduleOrProject,
      allSdks = model.existingSdks,
      fileSystem = model.fileSystem,
      targetPanelExtension = model.state.targetPanelExtension.get(),
      isAssociateWithModule = true,
    )
    return sdk
  }

  override fun createStatisticsInfo(target: PythonInterpreterCreationTargets): InterpreterStatisticsInfo {
    val statisticsTarget = target.toStatisticsField() // todo fix for wsl
    return InterpreterStatisticsInfo(
      type = InterpreterType.REGULAR,
      target = statisticsTarget,
      globalSitePackage = false,
      makeAvailableToAllProjects = false,
      previouslyConfigured = true,
      isWSLContext = false, // todo fix for wsl
      creationMode = InterpreterCreationMode.CUSTOM
    )
  }
}