// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.application.EDT
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.and
import com.intellij.openapi.observable.util.isNotNull
import com.intellij.openapi.observable.util.or
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.validation.WHEN_PROPERTY_CHANGED
import com.intellij.platform.eel.EelApi
import com.intellij.python.pytools.backend.Version
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindText
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.launchOnShow
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.Result
import com.jetbrains.python.TraceContext
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathFlows
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.add.collector.PythonNewInterpreterAddedCollector
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMode.BASE_CONDA
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMode.CUSTOM
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMode.PROJECT_UV
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMode.PROJECT_VENV
import com.jetbrains.python.sdk.add.v2.conda.selectCondaEnvironment
import com.jetbrains.python.sdk.add.v2.uv.UvInterpreterSection
import com.jetbrains.python.sdk.add.v2.venv.setupVirtualenv
import com.jetbrains.python.sdk.add.v2.venv.venvBaseVersionError
import com.jetbrains.python.statistics.InterpreterCreationMode
import com.jetbrains.python.statistics.InterpreterTarget
import com.jetbrains.python.statistics.InterpreterType
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.awt.Component

interface PySdkPanelBuilder {
  /**
   * Performs only static initialization using Kotlin DSL [com.intellij.ui.dsl], without access to [CoroutineScope].
   */
  @RequiresEdt
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
  private val limitExistingEnvironments: Boolean = true,
  private val eel: EelApi
) : PySdkPanelBuilder, PySdkCreator {
  private val propertyGraph = PropertyGraph()

  private val initMutex = Mutex()

  private var selectedMode = propertyGraph.property(PythonInterpreterSelectionMode.entries.first())
  private var _projectVenv = propertyGraph.booleanProperty(selectedMode, PROJECT_VENV)
  private var _baseConda = propertyGraph.booleanProperty(selectedMode, BASE_CONDA)
  private var _custom = propertyGraph.booleanProperty(selectedMode, CUSTOM)

  // false when the selected Project venv base interpreter can't be used to create a venv (< 3.8)
  private val _venvBaseValid = propertyGraph.property(true)
  private var venvHint = propertyGraph.property("")

  private lateinit var pythonBaseVersionComboBox: PythonInterpreterComboBox<PathHolder.Eel>
  private lateinit var executablePath: ValidatedPathField<Version, PathHolder.Eel, ValidatedPath.Executable<PathHolder.Eel>>
  private lateinit var uvSection: UvInterpreterSection

  private suspend fun updateVenvLocationHint(): Unit = withContext(Dispatchers.EDT) {
    val get = selectedMode.get()
    val projectPath = model.projectPathFlows.projectPathWithDefault.first().resolve(VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME).toString()
    when (get) {
      PROJECT_VENV -> venvHint.set(message("sdk.create.simple.venv.hint", projectPath))
      BASE_CONDA -> venvHint.set(message("sdk.create.simple.conda.hint"))
      PROJECT_UV -> venvHint.set(message("sdk.create.simple.uv.hint", projectPath))
      CUSTOM -> venvHint.set("")
    }
  }

  private lateinit var custom: PythonAddCustomInterpreter<PathHolder.Eel>
  private lateinit var model: PythonMutableTargetAddInterpreterModel<PathHolder.Eel>

  @RequiresEdt
  override fun buildPanel(outerPanel: Panel, projectPathFlows: ProjectPathFlows) {
    // To support project creation on new WSL we would need to derive eel from pathFlow
    // That means we would need to rebuild the whole UI as soon as it changes
    // While it is possible, it cost time, and we postpone for now
    model = PythonLocalAddInterpreterModel(projectPathFlows, EelFileSystem(eel))
    model.navigator.selectionMode = selectedMode
    propertyGraph.dependsOn(_venvBaseValid, model.state.baseInterpreter, deleteWhenChildModified = false) {
      model.state.baseInterpreter.get()?.let { venvBaseVersionError(it) == null } ?: true
    }
    uvSection = UvInterpreterSection(model, selectedMode, propertyGraph)

    custom = PythonAddCustomInterpreter(model = model,
                                        module = null,
                                        errorSink = ErrorSink(),
                                        limitExistingEnvironments = limitExistingEnvironments,
                                        bestGuessCreateSdkInfo = CompletableDeferred(value = null))

    val validationRequestor = WHEN_PROPERTY_CHANGED(selectedMode)

    with(outerPanel) {
      if (PythonInterpreterSelectionMode.entries.size > 1) { // No need to show control with only one selection
        row(message("sdk.create.interpreter.type")) {
          segmentedButton(PythonInterpreterSelectionMode.entries) { text = message(it.nameKey) }.bind(selectedMode)
        }
      }

      pythonBaseVersionComboBox = pythonInterpreterComboBox(
        model.fileSystem,
        title = message("sdk.create.python.version"),
        selectedSdkProperty = model.state.baseInterpreter,
        validationRequestor = validationRequestor,
        onPathSelected = model::addManuallyAddedSystemPython,
        // The Python version combo is shown only for Project venv, which requires Python 3.8+.
        additionalValidation = { venvBaseVersionError(it) },
      ) {
        visibleIf(_projectVenv)
      }

      rowsRange {
        executablePath = validatablePathField(
          fileSystem = model.fileSystem,
          pathValidator = model.condaViewModel.toolValidator,
          validationRequestor = validationRequestor,
          labelText = message("sdk.create.custom.venv.executable.path", "conda"),
          missingExecutableText = message("sdk.create.custom.venv.missing.text", "conda"),
          installAction = createInstallCondaFix(model),
        )
      }.visibleIf(_baseConda)

      uvSection.setupUI(this, validationRequestor)

      row("") {
        comment("").bindText(venvHint)
      }.visibleIf((_projectVenv and _venvBaseValid) or (_baseConda and model.condaViewModel.condaExecutable.isNotNull()) or uvSection.hintVisiblePredicate() or _custom)

      rowsRange {
        custom.setupUI(this, validationRequestor)
      }.visibleIf(_custom)
    }

    model.navigator.restoreLastState(PythonInterpreterSelectionMode.entries) // restore the last UI state before init to prevent visual loading lags
  }

  override fun onShownInitialization(scopingComponent: Component) {
    scopingComponent.launchOnShow("${this::class.java} onShown initialization",
                                  TraceContext(message("trace.context.new.project.wizard"), null)) {
      initMutex.withLock {
        supervisorScope {
          initialize(this@supervisorScope)
        }
      }
    }
  }

  private suspend fun initialize(scope: CoroutineScope) {
    model.initialize(scope)

    pythonBaseVersionComboBox.initialize(scope, model.baseInterpreters)
    executablePath.initialize(scope)

    model.projectPathFlows.projectPathWithDefault.onEach { updateVenvLocationHint() }.launchIn(scope)
    selectedMode.afterChange(scope.asDisposable()) { scope.launch { updateVenvLocationHint() } }

    uvSection.onShown(scope)
    custom.onShown(scope)
  }

  override suspend fun createPythonModuleStructure(module: Module, createGitRepository: Boolean): PyResult<Unit> {
    return when (selectedMode.get()) {
      CUSTOM -> custom.currentSdkManager.createPythonModuleStructure(module, createGitRepository)
      PROJECT_UV -> uvSection.getUvCreator().createPythonModuleStructure(module, createGitRepository)
      PROJECT_VENV, BASE_CONDA -> Result.success(Unit)
    }
  }

  override suspend fun getSdk(moduleOrProject: ModuleOrProject): PyResult<Pair<Sdk, InterpreterStatisticsInfo>> {
    model.navigator.saveLastState()

    val sdk = when (selectedMode.get()) {
      PROJECT_VENV -> {
        val projectPath = model.projectPathFlows.projectPathWithDefault.first()
        // todo just keep venv path, all the rest is in the model
        val venvFolder = PathHolder.Eel(projectPath.resolve(VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME))
        model.setupVirtualenv(venvFolder, moduleOrProject)
      }
      BASE_CONDA -> model.selectCondaEnvironment(moduleOrProject, base = true)
      PROJECT_UV -> uvSection.getUvCreator().setupSdk(moduleOrProject)
      CUSTOM -> custom.currentSdkManager.setupSdk(moduleOrProject)
    }.getOr { return it }

    val statistics = withContext(Dispatchers.EDT) { createStatisticsInfo() }
    PythonNewInterpreterAddedCollector.logPythonNewInterpreterAdded(sdk, statistics.previouslyConfigured)
    return Result.success(Pair(sdk, statistics))
  }

  private fun createStatisticsInfo(): InterpreterStatisticsInfo = when (selectedMode.get()) {
    PROJECT_VENV -> InterpreterStatisticsInfo(type = InterpreterType.VIRTUALENV,
                                              target = InterpreterTarget.LOCAL,
                                              globalSitePackage = false,
                                              makeAvailableToAllProjects = false,
                                              previouslyConfigured = false,
                                              isWSLContext = false,
                                              creationMode = InterpreterCreationMode.SIMPLE)
    PROJECT_UV -> InterpreterStatisticsInfo(type = InterpreterType.UV,
                                            target = InterpreterTarget.LOCAL,
      // The uv page shows the "inherit packages" checkbox in this flow too, so report the real value.
                                            globalSitePackage = model.uvViewModel.inheritSitePackages.get(),
                                            makeAvailableToAllProjects = false,
                                            previouslyConfigured = false,
                                            isWSLContext = false,
                                            creationMode = InterpreterCreationMode.SIMPLE)
    BASE_CONDA -> InterpreterStatisticsInfo(type = InterpreterType.BASE_CONDA,
                                            target = InterpreterTarget.LOCAL,
                                            globalSitePackage = false,
                                            makeAvailableToAllProjects = false,
                                            previouslyConfigured = true,
                                            isWSLContext = false,
                                            creationMode = InterpreterCreationMode.SIMPLE)
    CUSTOM -> custom.createStatisticsInfo()
  }
}
