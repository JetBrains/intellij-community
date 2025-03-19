// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.uv

import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.text.nullize
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.uv.impl.setUvExecutable
import com.jetbrains.python.sdk.uv.setupNewUvSdkAndEnvUnderProgress
import com.jetbrains.python.statistics.InterpreterType
import com.jetbrains.python.venvReader.tryResolvePath
import java.nio.file.Path
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.errorProcessing.asPythonResult
import com.jetbrains.python.sdk.add.v2.CustomNewEnvironmentCreator
import com.jetbrains.python.sdk.add.v2.PythonMutableTargetAddInterpreterModel

internal class EnvironmentCreatorUv(model: PythonMutableTargetAddInterpreterModel) : CustomNewEnvironmentCreator("uv", model) {
  override val interpreterType: InterpreterType = InterpreterType.UV
  override val executable: ObservableMutableProperty<String> = model.state.uvExecutable
  override val installationVersion: String? = null

  override fun onShown() {
    // FIXME: validate base interpreters against pyprojecttoml version. See poetry
    basePythonComboBox.setItems(model.baseInterpreters)
  }

  override fun savePathToExecutableToProperties(path: Path?) {
    val savingPath = path ?: executable.get().nullize()?.let { Path.of(it) } ?: return
    setUvExecutable(savingPath)
  }

  override suspend fun setupEnvSdk(project: Project, module: Module?, baseSdks: List<Sdk>, projectPath: String, homePath: String?, installPackages: Boolean): Result<Sdk, PyError> {
    val workingDir = module?.basePath?.let { tryResolvePath(it) } ?: project.basePath?.let { tryResolvePath(it) }
    if (workingDir == null) {
      return kotlin.Result.failure<Sdk>(Exception("working dir is not specified for uv environment setup")).asPythonResult()
    }

    val python = homePath?.let { Path.of(it) }
    return setupNewUvSdkAndEnvUnderProgress(project, workingDir, baseSdks, python).asPythonResult()
  }

  override suspend fun detectExecutable() {
    model.detectUvExecutable()
  }
}