// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.util.text.nullize
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.sdk.poetry.poetryPath
import com.jetbrains.python.sdk.poetry.setupPoetrySdkUnderProgress
import com.jetbrains.python.statistics.InterpreterCreationMode
import com.jetbrains.python.statistics.InterpreterType

class PoetryNewEnvironmentCreator(model: PythonMutableTargetAddInterpreterModel) : PythonNewEnvironmentCreator(model) {

  private lateinit var basePythonComboBox: PythonInterpreterComboBox

  override fun buildOptions(panel: Panel, validationRequestor: DialogValidationRequestor) {
    with(panel) {
      row(message("sdk.create.custom.base.python")) {
        basePythonComboBox = pythonInterpreterComboBox(model.state.baseInterpreter,
                                                       model,
                                                       model::addInterpreter,
                                                       model.interpreterLoading)
          .align(Align.FILL)
          .component
      }

      executableSelector(model.state.poetryExecutable,
                         validationRequestor,
                         message("sdk.create.custom.poetry.path"),
                         message("sdk.create.custom.poetry.missing.text")).component
    }
  }

  override fun onShown() {
    basePythonComboBox.setItems(model.baseInterpreters)

    //val savedPath = PropertiesComponent.getInstance().poetryPath
    //if (savedPath != null) {
    //  model.state.poetryExecutable.set(savedPath)
    //}
    //else {
    //  val modalityState = ModalityState.current().asContextElement()
    //  model.scope.launch(Dispatchers.IO) {
    //    val poetryExecutable = detectPoetryExecutable()
    //    withContext(Dispatchers.EDT + modalityState) {
    //      poetryExecutable?.let { model.state.poetryExecutable.set(it.path) }
    //    }
    //  }
    //}
  }


  override fun getOrCreateSdk(): Sdk {
    if (model is PythonLocalAddInterpreterModel) {
      PropertiesComponent.getInstance().poetryPath = model.state.poetryExecutable.get().nullize()
    }

    val selectedBasePython = model.state.baseInterpreter.get()!!
    val homePath = model.installPythonIfNeeded(selectedBasePython)

    val newSdk = setupPoetrySdkUnderProgress(null,
                                             null,
                                             model.baseSdks,
                                             model.projectPath.value,
                                             homePath, false)!!
    SdkConfigurationUtil.addSdk(newSdk)
    return newSdk
  }
  
  override fun createStatisticsInfo(target: PythonInterpreterCreationTargets): InterpreterStatisticsInfo {
    //val statisticsTarget = if (presenter.projectLocationContext is WslContext) InterpreterTarget.TARGET_WSL else target.toStatisticsField()
    val statisticsTarget = target.toStatisticsField() // todo fix for wsl
    return InterpreterStatisticsInfo(InterpreterType.POETRY,
                                     statisticsTarget,
                                     false,
                                     false,
                                     false,
                                     //presenter.projectLocationContext is WslContext,
                                     false, // todo fix for wsl
                                     InterpreterCreationMode.CUSTOM)
  }
}