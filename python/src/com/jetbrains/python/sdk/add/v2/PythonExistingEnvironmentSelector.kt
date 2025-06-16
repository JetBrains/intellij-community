// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.ui.dsl.builder.Panel
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.statistics.InterpreterCreationMode
import com.jetbrains.python.statistics.InterpreterType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map

class PythonExistingEnvironmentSelector(model: PythonAddInterpreterModel, private val module: Module?) : PythonExistingEnvironmentConfigurator(model) {

  private lateinit var comboBox: PythonInterpreterComboBox

  override fun setupUI(panel: Panel, validationRequestor: DialogValidationRequestor) {
    with(panel) {
      comboBox = pythonInterpreterComboBox(
        title = message("sdk.create.custom.python.path"),
        selectedSdkProperty = model.state.selectedInterpreter,
        model = model,
        validationRequestor = validationRequestor,
        onPathSelected = model::addInterpreter,
      )
    }
  }

  override fun onShown(scope: CoroutineScope) {
    val interpretersFlow = model.allInterpreters.map { sortForExistingEnvironment(it, module) }
    comboBox.initialize(scope, interpretersFlow)
  }

  override suspend fun getOrCreateSdk(moduleOrProject: ModuleOrProject): PyResult<Sdk> {
    // todo error handling, nullability issues
    return Result.success(setupSdkIfDetected(model.state.selectedInterpreter.get()!!, model.existingSdks)!!)
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