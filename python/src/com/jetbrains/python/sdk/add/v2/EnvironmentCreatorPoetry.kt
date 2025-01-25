// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.util.text.nullize
import com.intellij.util.xmlb.XmlSerializerUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.baseDir
import com.jetbrains.python.sdk.poetry.PoetryPyProjectTomlPythonVersionsService
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.poetry.configurePoetryEnvironment
import com.jetbrains.python.sdk.poetry.poetryPath
import com.jetbrains.python.sdk.poetry.poetryToml
import com.jetbrains.python.sdk.poetry.pyProjectToml
import com.jetbrains.python.sdk.poetry.setupPoetrySdkUnderProgress
import com.jetbrains.python.statistics.InterpreterType
import com.jetbrains.python.util.ErrorSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.pathString

internal class EnvironmentCreatorPoetry(model: PythonMutableTargetAddInterpreterModel, private val moduleOrProject: ModuleOrProject?) : CustomNewEnvironmentCreator("poetry", model) {
  override val interpreterType: InterpreterType = InterpreterType.POETRY
  override val executable: ObservableMutableProperty<String> = model.state.poetryExecutable
  override val installationVersion: String = "1.8.0"

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
  }

  override fun savePathToExecutableToProperties(path: Path?) {
    val savingPath = path?.pathString ?: executable.get().nullize() ?: return
    PropertiesComponent.getInstance().poetryPath = savingPath
  }

  override suspend fun setupEnvSdk(project: Project?, module: Module?, baseSdks: List<Sdk>, projectPath: String, homePath: String?, installPackages: Boolean): Result<Sdk> {
    module?.let { service<PoetryConfigService>().setInProjectEnv(it) }
    return setupPoetrySdkUnderProgress(project, module, baseSdks, projectPath, homePath, installPackages)
  }

  override suspend fun detectExecutable() {
    model.detectPoetryExecutable()
  }

  private fun addInProjectCheckbox(panel: Panel) {
    with(panel) {
      row {
        checkBox(PyBundle.message("python.sdk.poetry.dialog.add.new.environment.in.project.checkbox"))
          .bindSelected(service<PoetryConfigService>().state::isInProjectEnv)
      }
    }
  }
}

@Service(Service.Level.APP)
@State(name = "PyPoetrySettings", storages = [Storage("pyPoetrySettings.xml")])
internal class PoetryConfigService : PersistentStateComponent<PoetryConfigService> {
  var isInProjectEnv: Boolean = false

  override fun getState(): PoetryConfigService = this

  override fun loadState(state: PoetryConfigService) {
    XmlSerializerUtil.copyBean(state, this)
  }

  suspend fun setInProjectEnv(module: Module) {
    val hasPoetryToml = poetryToml(module) != null

    if (isInProjectEnv || hasPoetryToml) {
      val modulePath = withContext(Dispatchers.IO) { pyProjectToml(module)?.parent?.toNioPath() ?: module.basePath?.let { Path.of(it) } }
      configurePoetryEnvironment(modulePath, "virtualenvs.in-project", isInProjectEnv.toString(), "--local")
    }
  }
}