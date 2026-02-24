// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.poetry

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.python.community.impl.poetry.common.poetryPath
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.newProjectWizard.collector.PythonNewProjectWizardCollector
import com.jetbrains.python.poetry.PoetryPyProjectTomlPythonVersionsService
import com.jetbrains.python.poetry.findPoetryToml
import com.jetbrains.python.sdk.add.v2.CustomNewEnvironmentCreator
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMethod.SELECT_EXISTING
import com.jetbrains.python.sdk.add.v2.PythonMutableTargetAddInterpreterModel
import com.jetbrains.python.sdk.add.v2.PythonSupportedEnvironmentManagers.POETRY
import com.jetbrains.python.sdk.add.v2.PythonSupportedEnvironmentManagers.PYTHON
import com.jetbrains.python.sdk.add.v2.ToolValidator
import com.jetbrains.python.sdk.add.v2.ValidatedPath
import com.jetbrains.python.sdk.add.v2.VenvExistenceValidationState.Error
import com.jetbrains.python.sdk.add.v2.VenvExistenceValidationState.Invisible
import com.jetbrains.python.sdk.add.v2.getBasePath
import com.jetbrains.python.sdk.add.v2.getOrInstallBasePython
import com.jetbrains.python.sdk.add.v2.savePathForEelOnly
import com.jetbrains.python.sdk.baseDir
import com.jetbrains.python.sdk.poetry.configurePoetryEnvironment
import com.jetbrains.python.sdk.poetry.createNewPoetrySdk
import com.jetbrains.python.statistics.InterpreterType
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.exists

internal class EnvironmentCreatorPoetry<P : PathHolder>(
  model: PythonMutableTargetAddInterpreterModel<P>,
  private val module: Module?,
  errorSink: ErrorSink,
) : CustomNewEnvironmentCreator<P>("poetry", model, errorSink) {
  override val interpreterType: InterpreterType = InterpreterType.POETRY
  override val toolValidator: ToolValidator<P> = model.poetryViewModel.toolValidator
  override val installationVersion: String = "1.8.0"
  override val toolExecutable: ObservableProperty<ValidatedPath.Executable<P>?> = model.poetryViewModel.poetryExecutable
  override val toolExecutablePersister: suspend (P) -> Unit = { pathHolder ->
    savePathForEelOnly(pathHolder) { path -> PropertiesComponent.getInstance().poetryPath = path.toString() }
  }

  private val isInProjectEnvFlow = MutableStateFlow(service<PoetryConfigService>().state.isInProjectEnv)
  private val isInProjectEnvProp = propertyGraph.property(isInProjectEnvFlow.value)

  init {
    isInProjectEnvProp.afterChange {
      isInProjectEnvFlow.value = it
      service<PoetryConfigService>().state.isInProjectEnv = it
    }
  }

  override fun setupUI(panel: Panel, validationRequestor: DialogValidationRequestor) {
    super.setupUI(panel, validationRequestor)
    addInProjectCheckbox(panel)
  }

  override fun onShown(scope: CoroutineScope) {
    super.onShown(scope)

    scope.launch(Dispatchers.IO) {
      val moduleDir = model.getBasePath(module).let { VirtualFileManager.getInstance().findFileByNioPath(it) }

      val project = module?.project
      val validatedInterpreters = if (moduleDir != null && project != null) {
        PoetryPyProjectTomlPythonVersionsService.getInstance(project).validateInterpretersVersions(moduleDir, model.baseInterpreters)
      } else model.baseInterpreters

      withContext(Dispatchers.EDT) {
        basePythonComboBox.initialize(scope, validatedInterpreters)
      }
    }

    scope.launch(Dispatchers.EDT) {
      model.projectPathFlows.projectPathWithDefault
        .combine(isInProjectEnvFlow) { p, i -> Pair(p, i) }
        .collect { (path, isInProjectEnv) ->
          if (!isInProjectEnv) {
            venvExistenceValidationState.set(Invisible)
            return@collect
          }

          val venvPath = path.resolve(VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME)

          venvExistenceValidationState.set(
            if (venvPath.exists())
              Error(VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME)
            else
              Invisible
          )
        }
    }
  }

  override suspend fun setupEnvSdk(moduleBasePath: Path): PyResult<Sdk> {
    val basePythonBinaryPath = model.getOrInstallBasePython()

    module?.let { service<PoetryConfigService>().setInProjectEnv(it) }
    return when (basePythonBinaryPath) {
      is PathHolder.Eel -> createNewPoetrySdk(
        moduleBasePath = moduleBasePath,
        basePythonBinaryPath = basePythonBinaryPath.path,
        installPackages = false
      )
      else -> PyResult.localizedError(PyBundle.message("target.is.not.supported", basePythonBinaryPath))
    }
  }

  override fun onVenvSelectExisting() {
    PythonNewProjectWizardCollector.logExistingVenvFixUsed()

    if (module != null) {
      model.navigator.navigateTo(newMethod = SELECT_EXISTING, newManager = POETRY)
    }
    else {
      model.navigator.navigateTo(newMethod = SELECT_EXISTING, newManager = PYTHON)
    }
  }

  private fun addInProjectCheckbox(panel: Panel) {
    with(panel) {
      row("") {
        checkBox(PyBundle.message("python.sdk.poetry.dialog.add.new.environment.in.project.checkbox"))
          .bindSelected(isInProjectEnvProp)
      }
    }
  }
}

@Service(Service.Level.APP)
@State(name = "PyPoetrySettings", storages = [Storage("pyPoetrySettings.xml")])
private class PoetryConfigService :
  SerializablePersistentStateComponent<PoetryConfigService.PyPoetrySettingsState>(PyPoetrySettingsState()) {
  class PyPoetrySettingsState : BaseState() {
    var isInProjectEnv = false
  }

  suspend fun setInProjectEnv(module: Module) {
    val hasPoetryToml = findPoetryToml(module) != null
    if (state.isInProjectEnv || hasPoetryToml) {
      val modulePath = withContext(Dispatchers.IO) {
        PyProjectToml.findFile(module)?.parent?.toNioPath() ?: module.baseDir?.path?.let { Path.of(it) }
      }
      configurePoetryEnvironment(modulePath, "virtualenvs.in-project", state.isInProjectEnv.toString(), "--local")
    }
  }
}