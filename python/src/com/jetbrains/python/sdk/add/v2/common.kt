// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.icons.AllIcons
import com.intellij.openapi.components.Service
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.ui.dsl.builder.Panel
import com.jetbrains.python.sdk.pipenv.PIPENV_ICON
import com.jetbrains.python.sdk.poetry.POETRY_ICON
import icons.PythonIcons
import icons.PythonSdkIcons
import kotlinx.coroutines.CoroutineScope
import javax.swing.Icon


@Service(Service.Level.APP)
class PythonAddSdkService(val coroutineScope: CoroutineScope)

interface PythonTargetEnvironmentInterpreterCreator {
  fun buildPanel(outerPanel: Panel, validationRequestor: DialogValidationRequestor)
  fun onShown() {}
  fun getSdk(): Sdk
}

abstract class PythonAddEnvironment(val presenter: PythonAddInterpreterPresenter) {
  val state: PythonAddInterpreterState
    get() = presenter.state

  internal val propertyGraph
    get() = state.propertyGraph

  abstract fun buildOptions(panel: Panel, validationRequestor: DialogValidationRequestor)
  open fun onShown() {}
  abstract fun getOrCreateSdk(): Sdk
}

enum class PythonSupportedEnvironmentManagers(val nameKey: String, val icon: Icon) {
  VIRTUALENV("sdk.create.custom.virtualenv", PythonIcons.Python.Virtualenv),
  CONDA("sdk.create.custom.conda", PythonIcons.Python.Anaconda),
  POETRY("sdk.create.custom.poetry", POETRY_ICON),
  PIPENV("sdk.create.custom.pipenv", PIPENV_ICON),
  PYTHON("sdk.create.custom.python", PythonSdkIcons.Python)
}

enum class PythonInterpreterSelectionMode(val nameKey: String) {
  PROJECT_VENV("sdk.create.type.project.venv"),
  BASE_CONDA("sdk.create.type.base.conda"),
  CUSTOM("sdk.create.type.custom")
}

enum class PythonInterpreterCreationTargets(val nameKey: String, val icon: Icon) {
  LOCAL_MACHINE("sdk.create.targets.local", AllIcons.Nodes.HomeFolder),
  SSH("", AllIcons.Nodes.HomeFolder),
  DOCKER("", AllIcons.Nodes.HomeFolder)
}

enum class PythonInterpreterSelectionMethod {
  CREATE_NEW, SELECT_EXISTING
}
