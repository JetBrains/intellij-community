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
import com.jetbrains.python.sdk.pipenv.pipEnvPath
import com.jetbrains.python.sdk.pipenv.setupPipEnvSdkUnderProgress
import com.jetbrains.python.statistics.InterpreterCreationMode
import com.jetbrains.python.statistics.InterpreterType

class PipEnvNewEnvironmentCreator(model: PythonMutableTargetAddInterpreterModel) : PythonNewEnvironmentCreator(model) {

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

      executableSelector(model.state.pipenvExecutable,
                         validationRequestor,
                         message("sdk.create.custom.pipenv.path"),
                         message("sdk.create.custom.pipenv.missing.text")).component
    }

  }


  override fun onShown() {
    basePythonComboBox.setItems(model.baseInterpreters)

    //val savedPath = PropertiesComponent.getInstance().pipEnvPath
    //if (savedPath != null) {
    //  model.state.pipenvExecutable.set(savedPath)
    //}
    //else {
    //  val modalityState = ModalityState.current().asContextElement()
    //  model.scope.launch(Dispatchers.IO) {
    //    val detectedExecutable = detectPipEnvExecutable()
    //    withContext(Dispatchers.EDT + modalityState) {
    //      detectedExecutable?.let { model.state.pipenvExecutable.set(it.path) }
    //    }
    //  }
    //}
  }

  override fun getOrCreateSdk(): Sdk {
    if (model is PythonLocalAddInterpreterModel) {
      PropertiesComponent.getInstance().pipEnvPath = model.state.pipenvExecutable.get().nullize()
    }

    // todo think about better error handling
    val selectedBasePython = model.state.baseInterpreter.get()!!
    val homePath = model.installPythonIfNeeded(selectedBasePython)

    val newSdk = setupPipEnvSdkUnderProgress(null, null,
                                             model.baseSdks,
                                             model.projectPath.get(),
                                             homePath, false)!!
    SdkConfigurationUtil.addSdk(newSdk)
    return newSdk
  }


  override fun createStatisticsInfo(target: PythonInterpreterCreationTargets): InterpreterStatisticsInfo {
    //val statisticsTarget = if (presenter.projectLocationContext is WslContext) InterpreterTarget.TARGET_WSL else target.toStatisticsField()
    val statisticsTarget = target.toStatisticsField() // todo fix for wsl
    return InterpreterStatisticsInfo(InterpreterType.PIPENV,
                                     statisticsTarget,
                                     false,
                                     false,
                                     false,
                                     //presenter.projectLocationContext is WslContext,
                                     false, // todo fix for wsl
                                     InterpreterCreationMode.CUSTOM)
  }
}