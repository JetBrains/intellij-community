// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.or
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.ui.dsl.builder.*
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.configuration.PyConfigurableInterpreterList
import com.jetbrains.python.newProject.steps.ProjectSpecificSettingsStep
import com.jetbrains.python.sdk.add.target.conda.TargetEnvironmentRequestCommandExecutor
import com.jetbrains.python.sdk.add.target.conda.createCondaSdkFromExistingEnv
import com.jetbrains.python.sdk.add.target.conda.suggestCondaPath
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMode.*
import com.jetbrains.python.sdk.configuration.PyProjectVirtualEnvConfiguration
import com.jetbrains.python.sdk.findBaseSdks
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
  private lateinit var baseCondaEnv: PyCondaEnv

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

  private val custom = PythonAddCustomInterpreter(state)


  fun buildPanel(outerPanel: Panel) {
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

    basePythonSdks.afterChange {
      pythonBaseVersionComboBox.removeAllItems()
      it.forEach { pythonBaseVersionComboBox.addItem(it) }
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
      state.scope.launch(Dispatchers.Default + modalityState) {
        val existingSdks = PyConfigurableInterpreterList.getInstance(null).getModel().sdks.toList()
        val baseSdks = findBaseSdks(existingSdks, null, UserDataHolderBase())
        val allValidSdks = ProjectSpecificSettingsStep.getValidPythonSdks(existingSdks)
        val validBaseSdks = ProjectSpecificSettingsStep.getValidPythonSdks(baseSdks)

        withContext(Dispatchers.Main + modalityState) {
          pythonBaseVersionComboBox.removeAllItems()
          validBaseSdks.forEach { pythonBaseVersionComboBox.addItem(it) }
          basePythonSdks.set(validBaseSdks)
          allExistingSdks.set(allValidSdks)

          updateVenvLocationHint()
        }
      }


      // todo maybe do this when env requested
      state.scope.launch(Dispatchers.Default + modalityState) {
        val condaPath = suggestCondaPath() ?: return@launch // todo make conda a executableSelector component
        val commandExecutor = TargetEnvironmentRequestCommandExecutor(LocalTargetEnvironmentRequest())
        val environments = PyCondaEnv.getEnvs(commandExecutor, condaPath)
        val baseConda = environments.getOrThrow().find { env -> env.envIdentity.let { it is PyCondaEnvIdentity.UnnamedEnv && it.isBase } }
        withContext(Dispatchers.Main + modalityState) {
          condaExecutable.set(condaPath)
          baseCondaEnv = baseConda!!
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

  fun getSdk(): Sdk? {
    return when (selectedMode.get()) {
      PROJECT_VENV -> {
        val venvPath = projectPath.get() + File.separator + ".venv"
        return PyProjectVirtualEnvConfiguration.createVirtualEnvSynchronously(pythonBaseVersion.get(), basePythonSdks.get(), venvPath,
                                                                              projectPath.get(),
                                                                              null, null)
      }
      BASE_CONDA -> {
        runWithModalProgressBlocking(ModalTaskOwner.guess(), message("sdk.create.custom.conda.select.progress"), TaskCancellation.nonCancellable()) {
          PyCondaCommand(condaExecutable.get(), null)
            .createCondaSdkFromExistingEnv(baseCondaEnv.envIdentity, basePythonSdks.get(), ProjectManager.getInstance().defaultProject)
        }
      }
      CUSTOM -> custom.getSdk()
    }
  }
}