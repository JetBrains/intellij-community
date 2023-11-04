// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.util.text.nullize
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.sdk.pipenv.detectPipEnvExecutable
import com.jetbrains.python.sdk.pipenv.pipEnvPath
import com.jetbrains.python.sdk.pipenv.setupPipEnvSdkUnderProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PipEnvNewEnvironmentCreator(presenter: PythonAddInterpreterPresenter) : PythonAddEnvironment(presenter) {
  private val executable = propertyGraph.property(UNKNOWN_EXECUTABLE)
  private lateinit var pipEnvPathField: TextFieldWithBrowseButton
  private lateinit var basePythonComboBox: ComboBox<String?>

  override fun buildOptions(panel: Panel, validationRequestor: DialogValidationRequestor) {
    with(panel) {
      row(message("sdk.create.custom.base.python")) {
        basePythonComboBox =
          pythonBaseInterpreterComboBox(presenter, presenter.basePythonSdksFlow, presenter.detectingSdks, presenter.basePythonHomePath)
            .align(Align.FILL)
            .component
      }

      pipEnvPathField = executableSelector(executable,
                                           validationRequestor,
                                           message("sdk.create.custom.pipenv.path"),
                                           message("sdk.create.custom.pipenv.missing.text")).component
    }

  }


  override fun onShown() {
    val savedPath = PropertiesComponent.getInstance().pipEnvPath
    if (savedPath != null) {
      executable.set(savedPath)
    }
    else {
      val modalityState = ModalityState.current().asContextElement()
      state.scope.launch(Dispatchers.IO) {
        val detectedExecutable = detectPipEnvExecutable()
        withContext(Dispatchers.EDT + modalityState) {
          detectedExecutable?.let { executable.set(it.path) }
        }
      }
    }
  }

  override fun getOrCreateSdk(): Sdk {
    PropertiesComponent.getInstance().pipEnvPath = pipEnvPathField.text.nullize()
    return setupPipEnvSdkUnderProgress(null, null, state.basePythonSdks.get(), state.projectPath.get(),
                                       state.basePythonVersion.get()!!.homePath, false)!!
  }
}