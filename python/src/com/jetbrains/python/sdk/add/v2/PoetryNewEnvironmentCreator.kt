// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.text.nullize
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.baseDir
import com.jetbrains.python.sdk.poetry.PyProjectTomlPythonVersionsService
import com.jetbrains.python.PythonHelpersLocator
import com.jetbrains.python.sdk.poetry.poetryPath
import com.jetbrains.python.sdk.poetry.setupPoetrySdkUnderProgress
import com.jetbrains.python.statistics.InterpreterType
import kotlinx.coroutines.flow.StateFlow

class PoetryNewEnvironmentCreator(model: PythonMutableTargetAddInterpreterModel, private val moduleOrProject: ModuleOrProject?) : CustomNewEnvironmentCreator("poetry", model) {
  override val interpreterType: InterpreterType = InterpreterType.POETRY
  override val executable: ObservableMutableProperty<String> = model.state.poetryExecutable
  override val installationScript = PythonHelpersLocator.findPathInHelpers("pycharm_poetry_installer.py")

  override fun onShown() {
    val moduleDir = when (moduleOrProject) {
      is ModuleOrProject.ModuleAndProject -> moduleOrProject.module.baseDir
      is ModuleOrProject.ProjectOnly -> moduleOrProject.project.projectFile
      null -> null
    }

    val validatedInterpreters =
      if (moduleDir != null) {
        PyProjectTomlPythonVersionsService.instance.validateInterpretersVersions(moduleDir, model.baseInterpreters)
          as? StateFlow<List<PythonSelectableInterpreter>> ?: model.baseInterpreters
      }
      else {
        model.baseInterpreters
      }

    basePythonComboBox.setItems(validatedInterpreters)
  }

  override fun savePathToExecutableToProperties() {
    PropertiesComponent.getInstance().poetryPath = executable.get().nullize()
  }

  override fun setupEnvSdk(project: Project?, module: Module?, baseSdks: List<Sdk>, projectPath: String, homePath: String?, installPackages: Boolean): Sdk? =
    setupPoetrySdkUnderProgress(project, module, baseSdks, projectPath, homePath, installPackages)

  override suspend fun detectExecutable() {
    model.detectPoetryExecutable()
  }
}