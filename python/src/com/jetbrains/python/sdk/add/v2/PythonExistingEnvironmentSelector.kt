// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.jetbrains.python.PyBundle.message

class PythonExistingEnvironmentSelector(presenter: PythonAddInterpreterPresenter) : PythonAddEnvironment(presenter) {

  override fun buildOptions(panel: Panel, validationRequestor: DialogValidationRequestor) {
    with(panel) {
      row(message("sdk.create.custom.python.path")) {
        pythonBaseInterpreterComboBox(presenter, presenter.allSdksFlow, presenter.detectingSdks, presenter.state.selectedVenvPath)
          .align(Align.FILL)
      }
    }
  }

  override fun getOrCreateSdk(): Sdk {
    return state.selectedVenv.get()!!
  }
}