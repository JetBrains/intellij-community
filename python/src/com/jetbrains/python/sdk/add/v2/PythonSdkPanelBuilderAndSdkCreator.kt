// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.application.EDT
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.and
import com.intellij.openapi.observable.util.notEqualsTo
import com.intellij.openapi.observable.util.or
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.validation.WHEN_PROPERTY_CHANGED
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindText
import com.intellij.util.asDisposable
import com.intellij.util.ui.launchOnShow
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathFlows
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.add.collector.PythonNewInterpreterAddedCollector
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMode.*
import com.jetbrains.python.statistics.InterpreterCreationMode
import com.jetbrains.python.statistics.InterpreterTarget
import com.jetbrains.python.statistics.InterpreterType
import com.jetbrains.python.util.ShowingMessageErrorSync
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.awt.Component

interface PySdkPanelBuilder {
  /**
   * Performs only static initialization using Kotlin DSL [com.intellij.ui.dsl], without access to [CoroutineScope].
   */
  fun buildPanel(outerPanel: Panel, projectPathFlows: ProjectPathFlows)

  /**
   * This method is used to bind the background initialization of the panel to a specific [scopingComponent] showing scope.
   * This dynamic background initialization may occur each time the anchor component becomes visible.
   * The previous scope and all started coroutines are canceled when the anchor component is hidden.

   * The anchor component must be visible as long as you want to keep the background processes running.
   * Usually the anchor is the DialogPanel itself or one of its parents.
   */
  fun onShownInitialization(scopingComponent: Component)
}

/**
 * If `onlyAllowedInterpreterTypes` then only these types are displayed. All types displayed otherwise
 */
internal class PythonSdkPanelBuilderAndSdkCreator(
  onlyAllowedInterpreterTypes: Set<PythonInterpreterSelectionMode>? = null,
  private val errorSink: ErrorSink,
  private val module: Module? = null,
  private val limitExistingEnvironments: Boolean = true,
) : PySdkPanelBuilder, PySdkCreator {
  private val propertyGraph = PropertyGraph()
  private val allowedInterpreterTypes = (onlyAllowedInterpreterTypes ?: PythonInterpreterSelectionMode.entries).also {
    assert(it.isNotEmpty()) {
      "When provided, onlyAllowedInterpreterTypes shouldn't be empty"
    }
  }

  private val initMutex = Mutex()

  private var selectedMode = propertyGraph.property(this.allowedInterpreterTypes.first())
  private var _projectVenv = propertyGraph.booleanProperty(selectedMode, PROJECT_VENV)
  private var _baseConda = propertyGraph.booleanProperty(selectedMode, BASE_CONDA)
  private var _custom = propertyGraph.booleanProperty(selectedMode, CUSTOM)
  private var venvHint = propertyGraph.property("")

  private lateinit var pythonBaseVersionComboBox: PythonInterpreterComboBox

  private suspend fun updateVenvLocationHint(): Unit = withContext(Dispatchers.EDT) {
    val get = selectedMode.get()
    val projectPath = model.projectPathFlows.projectPathWithDefault.first().resolve(VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME).toString()
    if (get == PROJECT_VENV) venvHint.set(message("sdk.create.simple.venv.hint", projectPath))
    else if (get == BASE_CONDA && PROJECT_VENV in allowedInterpreterTypes) venvHint.set(message("sdk.create.simple.conda.hint"))
  }

  private lateinit var custom: PythonAddCustomInterpreter
  private lateinit var model: PythonMutableTargetAddInterpreterModel

  override fun buildPanel(outerPanel: Panel, projectPathFlows: ProjectPathFlows) {
    model = PythonLocalAddInterpreterModel(projectPathFlows)
    model.navigator.selectionMode = selectedMode

    custom = PythonAddCustomInterpreter(
      model = model,
      module = module,
      errorSink = ShowingMessageErrorSync,
      limitExistingEnvironments = limitExistingEnvironments
    )

    val validationRequestor = WHEN_PROPERTY_CHANGED(selectedMode)

    with(outerPanel) {
      if (allowedInterpreterTypes.size > 1) { // No need to show control with only one selection
        row(message("sdk.create.interpreter.type")) {
          segmentedButton(allowedInterpreterTypes) { text = message(it.nameKey) }
            .bind(selectedMode)
        }
      }

      pythonBaseVersionComboBox = pythonInterpreterComboBox(
        title = message("sdk.create.python.version"),
        selectedSdkProperty = model.state.baseInterpreter,
        model = model,
        validationRequestor = validationRequestor,
        onPathSelected = model::addInterpreter
      ) {
        visibleIf(_projectVenv)
      }

      rowsRange {
        executableSelector(model.state.condaExecutable,
                           validationRequestor,
                           message("sdk.create.custom.venv.executable.path", "conda"),
                           message("sdk.create.custom.venv.missing.text", "conda"),
                           createInstallCondaFix(model, errorSink))
      }.visibleIf(_baseConda)

      row("") {
        comment("").bindText(venvHint)
      }.visibleIf(_projectVenv or (_baseConda and model.state.condaExecutable.notEqualsTo(UNKNOWN_EXECUTABLE)))

      rowsRange {
        custom.setupUI(this, validationRequestor)
      }.visibleIf(_custom)
    }

    model.navigator.restoreLastState(allowedInterpreterTypes) // restore the last UI state before init to prevent visual loading lags
  }

  override fun onShownInitialization(scopingComponent: Component) {
    scopingComponent.launchOnShow("${this::class.java} onShown initialization") {
      initMutex.withLock {
        supervisorScope {
          initialize(this@supervisorScope)
        }
      }
    }
  }

  private fun initialize(scope: CoroutineScope) {
    model.initialize(scope)

    pythonBaseVersionComboBox.initialize(scope, model.baseInterpreters)

    model.projectPathFlows.projectPathWithDefault.onEach { updateVenvLocationHint() }.launchIn(scope)
    selectedMode.afterChange(scope.asDisposable()) { scope.launch { updateVenvLocationHint() } }

    custom.onShown(scope)
  }

  override suspend fun createPythonModuleStructure(module: Module): PyResult<Unit> {
    return when (selectedMode.get()) {
      CUSTOM -> custom.currentSdkManager.createPythonModuleStructure(module)
      else -> Result.success(Unit)
    }
  }

  override suspend fun getSdk(moduleOrProject: ModuleOrProject): PyResult<Pair<Sdk, InterpreterStatisticsInfo>> {
    model.navigator.saveLastState()

    val sdk = when (selectedMode.get()) {
      PROJECT_VENV -> {
        val projectPath = model.projectPathFlows.projectPathWithDefault.first()
        // todo just keep venv path, all the rest is in the model
        model.setupVirtualenv(projectPath.resolve(VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME), moduleOrProject)
      }
      BASE_CONDA -> model.selectCondaEnvironment(base = true)
      CUSTOM -> custom.currentSdkManager.getOrCreateSdk(moduleOrProject)
    }.getOr { return it }

    val statistics = withContext(Dispatchers.EDT) { createStatisticsInfo() }
    PythonNewInterpreterAddedCollector.logPythonNewInterpreterAdded(sdk, statistics.previouslyConfigured)
    return Result.success(Pair(sdk, statistics))
  }

  private fun createStatisticsInfo(): InterpreterStatisticsInfo = when (selectedMode.get()) {
    PROJECT_VENV -> InterpreterStatisticsInfo(
      type = InterpreterType.VIRTUALENV,
      target = InterpreterTarget.LOCAL,
      globalSitePackage = false,
      makeAvailableToAllProjects = false,
      previouslyConfigured = false,
      isWSLContext = false,
      creationMode = InterpreterCreationMode.SIMPLE
    )
    BASE_CONDA -> InterpreterStatisticsInfo(
      type = InterpreterType.BASE_CONDA,
      target = InterpreterTarget.LOCAL,
      globalSitePackage = false,
      makeAvailableToAllProjects = false,
      previouslyConfigured = true,
      isWSLContext = false,
      creationMode = InterpreterCreationMode.SIMPLE
    )
    CUSTOM -> custom.createStatisticsInfo()
  }
}