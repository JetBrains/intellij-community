// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.util.text.nullize
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.sdk.add.WslContext
import com.jetbrains.python.sdk.poetry.detectPoetryExecutable
import com.jetbrains.python.sdk.poetry.poetryPath
import com.jetbrains.python.sdk.poetry.setupPoetrySdkUnderProgress
import com.jetbrains.python.statistics.InterpreterCreationMode
import com.jetbrains.python.statistics.InterpreterTarget
import com.jetbrains.python.statistics.InterpreterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PoetryNewEnvironmentCreator(presenter: PythonAddInterpreterPresenter) : PythonAddEnvironment(presenter) {

  val executable = propertyGraph.property(UNKNOWN_EXECUTABLE)
  private val basePythonVersion = propertyGraph.property<Sdk?>(initial = null)
  private lateinit var basePythonComboBox: ComboBox<Sdk?>
  override fun buildOptions(panel: Panel, validationRequestor: DialogValidationRequestor) {
    with(panel) {
      row(message("sdk.create.custom.base.python")) {
        basePythonComboBox = pythonInterpreterComboBox(basePythonVersion,
                                                       presenter,
                                                       presenter.basePythonSdksFlow,
                                                       presenter::addBasePythonInterpreter)
            .align(Align.FILL)
            .component
      }

      executableSelector(executable,
                         validationRequestor,
                         message("sdk.create.custom.poetry.path"),
                         message("sdk.create.custom.poetry.missing.text")).component
    }
  }

  override fun onShown() {
    val savedPath = PropertiesComponent.getInstance().poetryPath
    if (savedPath != null) {
      executable.set(savedPath)
    }
    else {
      val modalityState = ModalityState.current().asContextElement()
      state.scope.launch(Dispatchers.IO) {
        val poetryExecutable = detectPoetryExecutable()
        withContext(Dispatchers.EDT + modalityState) {
          poetryExecutable?.let { executable.set(it.path) }
        }
      }
    }
  }


  override fun getOrCreateSdk(): Sdk? {
    PropertiesComponent.getInstance().poetryPath = executable.get().nullize()
    val baseSdk = installBaseSdk(basePythonVersion.get()!!, state.allSdks.get()) ?: return null
    val newSdk = setupPoetrySdkUnderProgress(null, null, state.basePythonSdks.get(), state.projectPath.get(),
                                             baseSdk.homePath, false)!!
    SdkConfigurationUtil.addSdk(newSdk)
    return newSdk
  }
  
  override fun createStatisticsInfo(target: PythonInterpreterCreationTargets): InterpreterStatisticsInfo {
    val statisticsTarget = if (presenter.projectLocationContext is WslContext) InterpreterTarget.TARGET_WSL else target.toStatisticsField()
    return InterpreterStatisticsInfo(InterpreterType.POETRY,
                                     statisticsTarget,
                                     false,
                                     false,
                                     false,
                                     presenter.projectLocationContext is WslContext,
                                     InterpreterCreationMode.CUSTOM)
  }
}