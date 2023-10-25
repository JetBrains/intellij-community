// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.or
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.dsl.builder.*
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.configuration.PyConfigurableInterpreterList
import com.jetbrains.python.newProject.steps.ProjectSpecificSettingsStep
import com.jetbrains.python.sdk.add.target.conda.createCondaSdkFromExistingEnv
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMode.*
import com.jetbrains.python.sdk.configuration.createVirtualEnvSynchronously
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

class PythonAddNewEnvironmentPanel(val projectPath: ObservableProperty<String>) {
  private val propertyGraph = PropertyGraph()

  private var selectedMode = propertyGraph.property(PROJECT_VENV)
  private var _projectVenv = propertyGraph.booleanProperty(selectedMode, PROJECT_VENV)
  private var _baseConda = propertyGraph.booleanProperty(selectedMode, BASE_CONDA)
  private var _custom = propertyGraph.booleanProperty(selectedMode, CUSTOM)

  private val allExistingSdks = propertyGraph.property<List<Sdk>>(emptyList())
  private val basePythonSdks = propertyGraph.property<List<Sdk>>(emptyList())
  private val pythonBaseVersion = propertyGraph.property<Sdk?>(null)

  private val condaExecutable = propertyGraph.property("")
  private var venvHint = propertyGraph.property("")

  private lateinit var pythonBaseVersionComboBox: ComboBox<Sdk?>

  private var initialized = false

  private fun updateVenvLocationHint() {
    if (selectedMode.get() == PROJECT_VENV) venvHint.set(message("sdk.create.simple.venv.hint", projectPath.get() + File.separator))
    else if (selectedMode.get() == BASE_CONDA) venvHint.set(message("sdk.create.simple.conda.hint"))
  }

  val state = PythonAddInterpreterState(propertyGraph,
                                        projectPath,
                                        service<PythonAddSdkService>().coroutineScope,
                                        basePythonSdks,
                                        allExistingSdks,
                                        pythonBaseVersion,
                                        condaExecutable)

  private lateinit var presenter: PythonAddInterpreterPresenter

  private lateinit var custom: PythonAddCustomInterpreter


  fun buildPanel(outerPanel: Panel) {
    presenter = PythonAddInterpreterPresenter(state, uiContext = Dispatchers.EDT + ModalityState.current().asContextElement())
    custom = PythonAddCustomInterpreter(presenter)
    with(outerPanel) {
      row(message("sdk.create.interpreter.type")) {
        segmentedButton(PythonInterpreterSelectionMode.entries) { text = message(it.nameKey) }
          .bind(selectedMode)
      }.topGap(TopGap.SMALL)


      row(message("sdk.create.python.version")) {
        pythonBaseVersionComboBox = comboBox<Sdk?>(emptyList(), PythonSdkComboBoxListCellRenderer())
          .bindItem(pythonBaseVersion)
          .align(AlignX.FILL)
          .component
      }.visibleIf(_projectVenv)

      row(message("sdk.create.conda.executable.path")) {
        textFieldWithBrowseButton()
          .bindText(condaExecutable)
          .validationOnInput {
            if (!Paths.get(it.text).exists()) error("Executable does not exist") else null
          }
          .align(AlignX.FILL)
      }.visibleIf(_baseConda)

      row("") {
        comment("").bindText(venvHint)
      }.visibleIf(_projectVenv or _baseConda)

      rowsRange {
        custom.buildPanel(this)
      }.visibleIf(_custom)
    }

    projectPath.afterChange { updateVenvLocationHint() }
    selectedMode.afterChange { updateVenvLocationHint() }

    // todo why doesn't work?
    //venvHint.dependsOn(projectPath, ::updateVenvLocationHint)
    //venvHint.dependsOn(selectedMode, ::updateVenvLocationHint)
  }

  fun onShown() {
    if (!initialized) {
      initialized = true
      val modalityState = ModalityState.current().asContextElement()
      state.scope.launch(Dispatchers.EDT + modalityState) {
        val existingSdks = PyConfigurableInterpreterList.getInstance(null).getModel().sdks.toList()
        val allValidSdks = withContext(Dispatchers.IO) {
          ProjectSpecificSettingsStep.getValidPythonSdks(existingSdks)
        }
        allExistingSdks.set(allValidSdks)
        updateVenvLocationHint()
      }

      state.scope.launch(start = CoroutineStart.UNDISPATCHED) {
        presenter.basePythonSdksFlow.collectLatest { baseSdks ->
          withContext(presenter.uiContext) {
            pythonBaseVersionComboBox.removeAllItems()
            baseSdks.forEach { sdk -> pythonBaseVersionComboBox.addItem(sdk) }
          }
        }
      }

      custom.onShown()
    }
  }

  //fun validateCurrent(): Boolean {
  //  return when (selectedMode.get()) {
  //    PROJECT_VENV -> pythonBaseVersion.get().let { it != null && it.sdkSeemsValid }
  //    BASE_CONDA -> {
  //      val condaPath = condaExecutable.get()
  //      return condaPath != null
  //    }
  //    CUSTOM -> error("")
  //  }
  //}

  fun getSdk(): Sdk? =
    when (selectedMode.get()) {
      PROJECT_VENV -> {
        val venvPath = Path.of(projectPath.get(), ".venv")
        val venvPathOnTarget = presenter.getPathOnTarget(venvPath)
        createVirtualEnvSynchronously(pythonBaseVersion.get(), basePythonSdks.get(), venvPathOnTarget, projectPath.get(), null, null)
      }
      BASE_CONDA -> {
        runWithModalProgressBlocking(ModalTaskOwner.guess(), message("sdk.create.custom.conda.select.progress"),
                                     TaskCancellation.nonCancellable()) {
          presenter.createCondaCommand()
            .createCondaSdkFromExistingEnv(presenter.baseConda!!.envIdentity, basePythonSdks.get(),
                                           ProjectManager.getInstance().defaultProject)
        }
      }
      CUSTOM -> custom.getSdk()
    }
}