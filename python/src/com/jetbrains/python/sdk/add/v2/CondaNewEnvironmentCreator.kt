// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.add.WslContext
import com.jetbrains.python.sdk.add.target.conda.condaSupportedLanguages
import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest
import com.jetbrains.python.statistics.InterpreterCreationMode
import com.jetbrains.python.statistics.InterpreterTarget
import com.jetbrains.python.statistics.InterpreterType
import java.io.File

class CondaNewEnvironmentCreator(model: PythonMutableTargetAddInterpreterModel) : PythonNewEnvironmentCreator(model) {

  private lateinit var pythonVersion: ObservableMutableProperty<LanguageLevel>
  private lateinit var versionComboBox: ComboBox<LanguageLevel>

  override fun buildOptions(panel: Panel, validationRequestor: DialogValidationRequestor) {
    with(panel) {
      row(message("sdk.create.python.version")) {
        pythonVersion = propertyGraph.property(condaSupportedLanguages.first())
        versionComboBox = comboBox(condaSupportedLanguages, textListCellRenderer { it!!.toPythonVersion() })
          .bindItem(pythonVersion)
          .component
      }
      row(message("sdk.create.custom.conda.env.name")) {
        textField()
          .bindText(model.state.newCondaEnvName)
      }

      executableSelector(model.state.condaExecutable,
                         validationRequestor,
                         message("sdk.create.conda.executable.path"),
                         message("sdk.create.conda.missing.text"),
                         createInstallCondaFix(model))
        .displayLoaderWhen(model.condaEnvironmentsLoading, scope = model.scope, uiContext = model.uiContext)
    }
  }

  override fun onShown() {
    model.state.newCondaEnvName.set(model.projectPath.get().substringAfterLast(File.separator))
  }

  override fun getOrCreateSdk(): Sdk {
    return model.createCondaEnvironment(NewCondaEnvRequest.EmptyNamedEnv(pythonVersion.get(), model.state.newCondaEnvName.get()))
  }

  override fun createStatisticsInfo(target: PythonInterpreterCreationTargets): InterpreterStatisticsInfo {
    //val statisticsTarget = if (presenter.projectLocationContext is WslContext) InterpreterTarget.TARGET_WSL else target.toStatisticsField()
    val statisticsTarget = target.toStatisticsField() // todo fix for wsl
    return InterpreterStatisticsInfo(InterpreterType.CONDAVENV,
                                     statisticsTarget,
                                     false,
                                     false,
                                     false,
                                     //presenter.projectLocationContext is WslContext,
                                     false, // todo fix for wsl
                                     InterpreterCreationMode.CUSTOM)
  }
}