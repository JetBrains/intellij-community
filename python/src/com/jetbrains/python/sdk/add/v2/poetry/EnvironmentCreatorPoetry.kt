// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.poetry

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.*
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.python.community.impl.poetry.poetryPath
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.util.text.nullize
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.asPythonResult
import com.jetbrains.python.newProjectWizard.collector.PythonNewProjectWizardCollector
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.add.v2.CustomNewEnvironmentCreator
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMethod.SELECT_EXISTING
import com.jetbrains.python.sdk.add.v2.PythonMutableTargetAddInterpreterModel
import com.jetbrains.python.sdk.add.v2.PythonSelectableInterpreter
import com.jetbrains.python.sdk.add.v2.PythonSupportedEnvironmentManagers.POETRY
import com.jetbrains.python.sdk.add.v2.PythonSupportedEnvironmentManagers.PYTHON
import com.jetbrains.python.sdk.add.v2.VenvExistenceValidationState.Error
import com.jetbrains.python.sdk.add.v2.VenvExistenceValidationState.Invisible
import com.jetbrains.python.sdk.baseDir
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.poetry.*
import com.jetbrains.python.statistics.InterpreterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.pathString

internal class EnvironmentCreatorPoetry(model: PythonMutableTargetAddInterpreterModel, private val moduleOrProject: ModuleOrProject?) : CustomNewEnvironmentCreator("poetry", model) {
  override val interpreterType: InterpreterType = InterpreterType.POETRY
  override val executable: ObservableMutableProperty<String> = model.state.poetryExecutable
  override val installationVersion: String = "1.8.0"

  private val isInProjectEnvFlow = MutableStateFlow(service<PoetryConfigService>().state.isInProjectEnv)
  private val isInProjectEnvProp = propertyGraph.property(isInProjectEnvFlow.value)

  init {
    isInProjectEnvProp.afterChange {
      isInProjectEnvFlow.value = it
      service<PoetryConfigService>().state.isInProjectEnv = it
    }
  }

  override fun buildOptions(panel: Panel, validationRequestor: DialogValidationRequestor, errorSink: ErrorSink) {
    super.buildOptions(panel, validationRequestor, errorSink)
    addInProjectCheckbox(panel)
  }

  override fun onShown() {
    val moduleDir = when (moduleOrProject) {
      is ModuleOrProject.ModuleAndProject -> moduleOrProject.module.baseDir
      is ModuleOrProject.ProjectOnly -> moduleOrProject.project.projectFile
      null -> null
    }

    val validatedInterpreters =
      if (moduleDir != null) {
        PoetryPyProjectTomlPythonVersionsService.instance.validateInterpretersVersions(moduleDir, model.baseInterpreters)
          as? StateFlow<List<PythonSelectableInterpreter>> ?: model.baseInterpreters
      }
      else {
        model.baseInterpreters
      }

    basePythonComboBox.setItems(validatedInterpreters)

    model.scope.launch {
      model
        .myProjectPathFlows
        .projectPathWithDefault
        .combine(isInProjectEnvFlow) { p, i -> Pair(p, i) }
        .collect { (path, isInProjectEnv) ->
          if (!isInProjectEnv) {
            venvExistenceValidationState.set(Invisible)
            return@collect
          }

          val venvPath = path.resolve(".venv")

          venvExistenceValidationState.set(
            if (venvPath.exists())
              Error(Paths.get(".venv"))
            else
              Invisible
          )
        }
    }
  }

  override fun savePathToExecutableToProperties(path: Path?) {
    val savingPath = path?.pathString ?: executable.get().nullize() ?: return
    PropertiesComponent.getInstance().poetryPath = savingPath
  }

  override suspend fun setupEnvSdk(project: Project, module: Module?, baseSdks: List<Sdk>, projectPath: String, homePath: String?, installPackages: Boolean): PyResult<Sdk> {
    module?.let { service<PoetryConfigService>().setInProjectEnv(it) }
    return setupPoetrySdkUnderProgress(project, module, baseSdks, projectPath, homePath, installPackages).asPythonResult()
  }

  override suspend fun detectExecutable() {
    model.detectPoetryExecutable()
  }

  override fun onVenvSelectExisting() {
    PythonNewProjectWizardCollector.logExistingVenvFixUsed()

    if (moduleOrProject != null) {
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
          .bindSelected(service<PoetryConfigService>().state::isInProjectEnv)
      }
    }
  }
}

@Service(Service.Level.APP)
@State(name = "PyPoetrySettings", storages = [Storage("pyPoetrySettings.xml")])
private class PoetryConfigService : SerializablePersistentStateComponent<PoetryConfigService.PyPoetrySettingsState>(PyPoetrySettingsState()) {
  class PyPoetrySettingsState : BaseState() {
    var isInProjectEnv = false
  }

  suspend fun setInProjectEnv(module: Module) {
    val hasPoetryToml = poetryToml(module) != null
    if (state.isInProjectEnv || hasPoetryToml) {
      val modulePath = withContext(Dispatchers.IO) { pyProjectToml(module)?.parent?.toNioPath() ?: module.basePath?.let { Path.of(it) } }
      configurePoetryEnvironment(modulePath, "virtualenvs.in-project", state.isInProjectEnv.toString(), "--local")
    }
  }
}