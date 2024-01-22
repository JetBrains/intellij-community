// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.sdk.add.WslContext
import com.jetbrains.python.statistics.InterpreterCreationMode
import com.jetbrains.python.statistics.InterpreterTarget
import com.jetbrains.python.statistics.InterpreterType

class PythonExistingEnvironmentSelector(presenter: PythonAddInterpreterPresenter) : PythonAddEnvironment(presenter) {

  override fun buildOptions(panel: Panel, validationRequestor: DialogValidationRequestor) {
    with(panel) {
      row(message("sdk.create.custom.python.path")) {
        pythonInterpreterComboBox(presenter.state.selectedVenv,
                                  presenter,
                                  presenter.allSdksFlow,
                                  presenter::addPythonInterpreter)
          .align(Align.FILL)
      }
    }
  }

  override fun getOrCreateSdk(): Sdk {
    val selectedSdk = state.selectedVenv.get() ?: error("Unknown sdk selected")
    return setupSdkIfDetected(selectedSdk, state.allSdks.get())
  }

  override fun createStatisticsInfo(target: PythonInterpreterCreationTargets): InterpreterStatisticsInfo {
    val statisticsTarget = if (presenter.projectLocationContext is WslContext) InterpreterTarget.TARGET_WSL else target.toStatisticsField()
    return InterpreterStatisticsInfo(InterpreterType.REGULAR,
                                     statisticsTarget,
                                     false,
                                     false,
                                     true,
                                     presenter.projectLocationContext is WslContext,
                                     InterpreterCreationMode.CUSTOM)
  }
}