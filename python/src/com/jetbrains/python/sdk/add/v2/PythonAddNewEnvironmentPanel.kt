// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.and
import com.intellij.openapi.observable.util.notEqualsTo
import com.intellij.openapi.observable.util.or
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.validation.WHEN_PROPERTY_CHANGED
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.bindText
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMode.*
import com.jetbrains.python.statistics.InterpreterCreationMode
import com.jetbrains.python.statistics.InterpreterTarget
import com.jetbrains.python.statistics.InterpreterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Path

/**
 * If `onlyAllowedInterpreterTypes` then only these types are displayed. All types displayed otherwise
 */
class PythonAddNewEnvironmentPanel(val projectPath: ObservableProperty<String>, onlyAllowedInterpreterTypes: Set<PythonInterpreterSelectionMode>? = null) {

  private val propertyGraph = PropertyGraph()
  private val allowedInterpreterTypes = (onlyAllowedInterpreterTypes ?: PythonInterpreterSelectionMode.entries).also {
    assert(it.isNotEmpty()) {
      "When provided, onlyAllowedInterpreterTypes shouldn't be empty"
    }
  }

  private var selectedMode = propertyGraph.property(this.allowedInterpreterTypes.first())
  private var _projectVenv = propertyGraph.booleanProperty(selectedMode, PROJECT_VENV)
  private var _baseConda = propertyGraph.booleanProperty(selectedMode, BASE_CONDA)
  private var _custom = propertyGraph.booleanProperty(selectedMode, CUSTOM)
  private var venvHint = propertyGraph.property("")

  private lateinit var pythonBaseVersionComboBox: PythonInterpreterComboBox
  private var initialized = false

  private fun updateVenvLocationHint() {
    val get = selectedMode.get()
    if (get == PROJECT_VENV) venvHint.set(message("sdk.create.simple.venv.hint", projectPath.get() + File.separator))
    else if (get == BASE_CONDA && PROJECT_VENV in allowedInterpreterTypes) venvHint.set(message("sdk.create.simple.conda.hint"))
  }

  private lateinit var custom: PythonAddCustomInterpreter
  private lateinit var model: PythonMutableTargetAddInterpreterModel

  fun buildPanel(outerPanel: Panel) {
    //presenter = PythonAddInterpreterPresenter(state, uiContext = Dispatchers.EDT + ModalityState.current().asContextElement())
    model = PythonLocalAddInterpreterModel(PyInterpreterModelParams(service<PythonAddSdkService>().coroutineScope,
                                           Dispatchers.EDT + ModalityState.current().asContextElement(), projectPath))
    model.navigator.selectionMode = selectedMode
    //presenter.controller = model

    custom = PythonAddCustomInterpreter(model)

    val validationRequestor = WHEN_PROPERTY_CHANGED(selectedMode)


    with(outerPanel) {
      if (allowedInterpreterTypes.size > 1) { // No need to show control with only one selection
        row(message("sdk.create.interpreter.type")) {
          segmentedButton(allowedInterpreterTypes) { text = message(it.nameKey) }
            .bind(selectedMode)
        }.topGap(TopGap.MEDIUM)
      }

      row(message("sdk.create.python.version")) {
        pythonBaseVersionComboBox = pythonInterpreterComboBox(model.state.baseInterpreter,
                                                              model,
                                                              model::addInterpreter,
                                                              model.interpreterLoading)
          .align(AlignX.FILL)
          .component
      }.visibleIf(_projectVenv)

      rowsRange {
        executableSelector(model.state.condaExecutable,
                           validationRequestor,
                           message("sdk.create.conda.executable.path"),
                           message("sdk.create.conda.missing.text"),
                           createInstallCondaFix(model))
        //.displayLoaderWhen(presenter.detectingCondaExecutable, scope = presenter.scope, uiContext = presenter.uiContext)
      }.visibleIf(_baseConda)

      row("") {
        comment("").bindText(venvHint)
      }.visibleIf(_projectVenv or (_baseConda and model.state.condaExecutable.notEqualsTo(UNKNOWN_EXECUTABLE)))

      rowsRange {
        custom.buildPanel(this, validationRequestor)
      }.visibleIf(_custom)
    }

    projectPath.afterChange { updateVenvLocationHint() }
    selectedMode.afterChange { updateVenvLocationHint() }
  }

  fun onShown() {
    if (!initialized) {
      initialized = true
      val modalityState = ModalityState.current().asContextElement()
      model.scope.launch(Dispatchers.EDT + modalityState) {
        model.initialize()
        pythonBaseVersionComboBox.setItems(model.baseInterpreters)
        custom.onShown()

        updateVenvLocationHint()
      }

      model.navigator.restoreLastState(allowedInterpreterTypes)
    }
  }

  fun getSdk(): Sdk {
    model.navigator.saveLastState()
    return when (selectedMode.get()) {
      PROJECT_VENV -> {
        val projectPath = Path.of(projectPath.get())
        model.setupVirtualenv(projectPath.resolve(".venv"), // todo just keep venv path, all the rest is in the model
                              projectPath,
          //pythonBaseVersion.get()!!)
                              model.state.baseInterpreter.get()!!).getOrThrow()
      }
      BASE_CONDA -> model.selectCondaEnvironment(model.state.baseCondaEnv.get()!!.envIdentity)
      CUSTOM -> custom.currentSdkManager.getOrCreateSdk()
    }
  }


  fun createStatisticsInfo(): InterpreterStatisticsInfo = when (selectedMode.get()) {
    PROJECT_VENV -> InterpreterStatisticsInfo(InterpreterType.VIRTUALENV,
                                              InterpreterTarget.LOCAL,
                                              false,
                                              false,
                                              false,
      //presenter.projectLocationContext is WslContext,
                                              false,
                                              InterpreterCreationMode.SIMPLE)
    BASE_CONDA -> InterpreterStatisticsInfo(InterpreterType.BASE_CONDA,
                                            InterpreterTarget.LOCAL,
                                            false,
                                            false,
                                            true,
      //presenter.projectLocationContext is WslContext,
                                            false,
                                            InterpreterCreationMode.SIMPLE)
    CUSTOM -> custom.createStatisticsInfo()
  }
}