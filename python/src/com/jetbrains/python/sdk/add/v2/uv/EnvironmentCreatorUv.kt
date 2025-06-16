// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.uv

import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.text.nullize
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.newProjectWizard.collector.PythonNewProjectWizardCollector
import com.jetbrains.python.sdk.add.v2.CustomNewEnvironmentCreator
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMethod.SELECT_EXISTING
import com.jetbrains.python.sdk.add.v2.PythonMutableTargetAddInterpreterModel
import com.jetbrains.python.sdk.add.v2.PythonSupportedEnvironmentManagers.PYTHON
import com.jetbrains.python.sdk.add.v2.PythonSupportedEnvironmentManagers.UV
import com.jetbrains.python.sdk.add.v2.VenvExistenceValidationState
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.uv.impl.setUvExecutable
import com.jetbrains.python.sdk.uv.setupNewUvSdkAndEnvUnderProgress
import com.jetbrains.python.statistics.InterpreterType
import com.jetbrains.python.venvReader.tryResolvePath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists


internal class EnvironmentCreatorUv(
  model: PythonMutableTargetAddInterpreterModel,
  private val module: Module?,
  errorSink: ErrorSink,
) : CustomNewEnvironmentCreator("uv", model, errorSink) {
  override val interpreterType: InterpreterType = InterpreterType.UV
  override val executable: ObservableMutableProperty<String> = model.state.uvExecutable

  override fun onShown(scope: CoroutineScope) {
    super.onShown(scope)

    model.projectPathFlows.projectPathWithDefault.onEach { projectPath ->
      val venvPath = projectPath.resolve(".venv")
      venvExistenceValidationState.set(
        if (venvPath.exists())
          VenvExistenceValidationState.Error(Paths.get(".venv"))
        else
          VenvExistenceValidationState.Invisible
      )
    }.launchIn(scope)

  }

  override fun onVenvSelectExisting() {
    PythonNewProjectWizardCollector.logExistingVenvFixUsed()

    if (module != null) {
      model.navigator.navigateTo(newMethod = SELECT_EXISTING, newManager = UV)
    }
    else {
      model.navigator.navigateTo(newMethod = SELECT_EXISTING, newManager = PYTHON)
    }
  }

  override fun savePathToExecutableToProperties(path: Path?) {
    val savingPath = path ?: executable.get().nullize()?.let { Path.of(it) } ?: return
    setUvExecutable(savingPath)
  }

  override suspend fun setupEnvSdk(project: Project, module: Module?, baseSdks: List<Sdk>, projectPath: String, homePath: String?, installPackages: Boolean): PyResult<Sdk> {
    val workingDir = module?.basePath?.let { tryResolvePath(it) } ?: project.basePath?.let { tryResolvePath(it) }
    if (workingDir == null) {
      return PyResult.localizedError("working dir is not specified for uv environment setup")
    }

    val python = homePath?.let { Path.of(it) }
    return setupNewUvSdkAndEnvUnderProgress(project, workingDir, baseSdks, python)
  }

  override suspend fun detectExecutable() {
    model.detectUvExecutable()
  }
}